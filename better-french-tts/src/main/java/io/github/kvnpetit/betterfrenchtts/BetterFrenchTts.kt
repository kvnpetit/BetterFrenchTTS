package io.github.kvnpetit.betterfrenchtts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder
import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlRenderer
import io.github.kvnpetit.betterfrenchtts.voice.FrenchVoiceSelector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * High-level French TTS wrapper around Android's native [TextToSpeech].
 *
 * Selects a French voice and compiles a Kotlin DSL to native Android speech operations.
 * Native playback and strict offline voice selection are the defaults. SSML is opt-in.
 *
 * ## Quick start
 * ```kotlin
 * val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
 *     onReady = { it.speak("Bonjour le monde !") }
 * ))
 * ```
 *
 * ## DSL usage
 * ```kotlin
 * tts.speak {
 *     text("Bonjour.")
 *     pause(500)
 *     slow { text("Ceci est important.") }
 * }
 * ```
 *
 * ## Coroutine usage
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = tts.speakAndAwait("Bonjour le monde !")
 * }
 * ```
 *
 * @param context Android context (application context is used internally to avoid leaks).
 * @param config Optional [Config] to customize voice selection, presets, and callbacks.
 * @see Config
 * @see SpeechBuilder
 * @see SpeechResult
 */
class BetterFrenchTts internal constructor(
    context: Context,
    private val config: Config,
    focusAccess: AudioFocusAccess? = null,
    engineFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech
) {
    constructor(context: Context, config: Config = Config()) : this(
        context, config, engineFactory = { engineContext, listener ->
            if (config.enginePackage == null) TextToSpeech(engineContext, listener)
            else TextToSpeech(engineContext, listener, config.enginePackage)
        }
    )
    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    @Volatile private var closed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = ConcurrentHashMap<String, (SpeechResult) -> Unit>()
    private val playbackEpoch = AtomicLong(0)

    private var onSpeechStart: ((String) -> Unit)? = null
    private var onSpeechDone: ((String) -> Unit)? = null
    private var onSpeechError: ((String) -> Unit)? = null
    private var onDetailedSpeechError: ((SpeechResult.Error) -> Unit)? = null
    private var onWordHighlightCallback: ((WordHighlight) -> Unit)? = null

    private val ssmlTextOffsets = ConcurrentHashMap<String, Int>()

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusAccess = focusAccess ?: object : AudioFocusAccess {
        override fun request(request: AudioFocusRequest, listener: AudioManager.OnAudioFocusChangeListener) = audioManager.requestAudioFocus(request)
        override fun abandon(request: AudioFocusRequest) { audioManager.abandonAudioFocusRequest(request) }
    }
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(config.audioUsage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private val activeUtterances = ConcurrentHashMap.newKeySet<String>()
    private val voiceSelector = FrenchVoiceSelector(config.preferredVoiceNames, config.locale, config.offlineOnly, config.requireExactLocale)

    private val dictionary = PronunciationDictionary()
    private val nativePlayback = NativePlayback(::dispatchNativeStep) { action -> mainHandler.post { action() } }

    enum class PlaybackMode { NATIVE, SSML }

    private val queueItems = mutableListOf<QueueItem>()
    private val queueGeneration = AtomicLong(0)
    @Volatile private var queueIndex = -1
    @Volatile private var queueActive = false
    @Volatile private var queuePaused = false
    private var onQueueProgressCallback: ((QueueProgress) -> Unit)? = null
    private var onQueueFinishedCallback: (() -> Unit)? = null

    /** `true` once the TTS engine has been initialized and a French voice has been selected. */
    val isInitialized: Boolean get() = isReady

    /** The currently active [Voice], or `null` if the engine is not yet ready or no voice was found. */
    var currentVoice: Voice? = null
        private set

    /** `true` if a queue is currently playing (not paused). */
    val isQueuePlaying: Boolean get() = queueActive && !queuePaused

    /** `true` if a queue is active but paused. */
    val isQueuePaused: Boolean get() = queueActive && queuePaused

    /** Number of items currently in the queue. Returns 0 when no queue is active. */
    val queueSize: Int get() = synchronized(queueItems) { queueItems.size }

    /** Zero-based index of the item currently being spoken, or -1 if no queue is active. */
    val currentQueuePosition: Int get() = queueIndex

    /**
     * Configuration for [BetterFrenchTts].
     *
     * @property defaultPreset Preset applied to every [speak] call when none is specified.
     * @property preferredVoiceNames Ordered list of preferred offline voice names.
     *   Voices are matched case-insensitively among the highest-quality candidates.
     * @property preprocessText When `true` (default), French text is normalized before synthesis:
     *   abbreviations are expanded, ordinals spelled out, time/units/currency converted to words, etc.
     * @property autoChunkLongText When `true` (default), text whose SSML exceeds ~4 000 characters
     *   is automatically split at natural boundaries before dispatching.
     * @property audioFocus Audio focus strategy used while speaking.
     *   Defaults to [AudioFocusMode.DUCK] which lowers other apps' volume during speech.
     * @property onReady Called on the **main thread** once the TTS engine is initialized.
     *   Receives the fully ready [BetterFrenchTts] instance.
     * @property onInitError Called on the **main thread** if TTS initialization fails.
     *   Receives the error status code from [android.speech.tts.TextToSpeech.OnInitListener].
     * @property playbackMode Native text/silences by default; SSML requires engine validation.
     * @property locale Preferred French locale; another French locale may be selected if unavailable.
     * @property offlineOnly Reject network-required and uninstalled voices when true.
     * @property enginePackage Optional installed engine package (Android may apply engine fallback).
     * @property normalization Configurable French normalization and regional number conventions.
     */
    data class Config(
        val defaultPreset: SpeechPreset = SpeechPreset.NEUTRAL,
        val preferredVoiceNames: List<String> = FrenchVoiceSelector.DEFAULT_PREFERRED_VOICES,
        val preprocessText: Boolean = true,
        val autoChunkLongText: Boolean = true,
        val audioFocus: AudioFocusMode = AudioFocusMode.DUCK,
        val onReady: ((BetterFrenchTts) -> Unit)? = null,
        val onInitError: ((Int) -> Unit)? = null,
        val playbackMode: PlaybackMode = PlaybackMode.NATIVE,
        val locale: Locale = Locale.FRANCE,
        val offlineOnly: Boolean = true,
        val enginePackage: String? = null,
        val normalization: FrenchTextPreprocessor.Options = FrenchTextPreprocessor.Options(),
        /** Fail initialization instead of selecting another French locale when true. */
        val requireExactLocale: Boolean = false,
        /** Audio usage shared by synthesis and focus requests. */
        val audioUsage: Int = AudioAttributes.USAGE_MEDIA,
        /** Default stops on focus loss; PAUSE_QUEUE preserves a queue for manual resume. */
        val focusLossBehavior: FocusLossBehavior = FocusLossBehavior.STOP,
    )

    enum class FocusLossBehavior { STOP, PAUSE_QUEUE, IGNORE }

    /**
     * Strategy for managing audio focus while speaking.
     *
     * Audio focus tells other apps (music players, podcasts, etc.) to lower their volume
     * or pause while this library is speaking.
     *
     * @see Config.audioFocus
     */
    enum class AudioFocusMode {
        /** Do not request audio focus. Other apps continue playing at full volume. */
        NONE,
        /** Duck (lower volume of) other apps while speaking. Recommended for short utterances. */
        DUCK,
        /** Pause other apps while speaking. They resume when speech finishes. Best for long content. */
        GAIN_TRANSIENT
    }

    init {
        tts = engineFactory(context.applicationContext, TextToSpeech.OnInitListener { status -> mainHandler.post {
            if (closed) return@post
            if (status == TextToSpeech.SUCCESS) {
                setup()
            } else {
                mainHandler.post { config.onInitError?.invoke(status) }
            }
        } })
    }

    private fun setup() {
        val engine = tts ?: return
        if (engine.setAudioAttributes(speechAudioAttributes) != TextToSpeech.SUCCESS) {
            config.onInitError?.invoke(TextToSpeech.ERROR)
            return
        }
        val languageResult = engine.setLanguage(config.locale)

        val bestVoice = voiceSelector.selectBestVoice(engine)
        if (languageResult < TextToSpeech.LANG_AVAILABLE || bestVoice == null || engine.setVoice(bestVoice) != TextToSpeech.SUCCESS) {
            mainHandler.post { if (!closed) config.onInitError?.invoke(TextToSpeech.LANG_NOT_SUPPORTED) }
            return
        } else {
            currentVoice = bestVoice
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id !in activeUtterances) return
                val epoch = playbackEpoch.get()
                if (onSpeechStart != null) mainHandler.post { if (!closed && playbackEpoch.get() == epoch) onSpeechStart?.invoke(id) }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: ""
                if (!activeUtterances.remove(id)) return
                ssmlTextOffsets.remove(id)
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Success)
                if (activeUtterances.isEmpty() && !nativePlayback.isBusy) abandonAudioFocus()
                if (onWordHighlightCallback != null || onSpeechDone != null) {
                    mainHandler.post {
                        onWordHighlightCallback?.invoke(WordHighlight(id, -1, -1))
                        onSpeechDone?.invoke(id)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleEngineError(utteranceId, null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) { handleEngineError(utteranceId, errorCode) }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                val id = utteranceId ?: return
                if (!activeUtterances.remove(id)) return
                ssmlTextOffsets.remove(id)
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Error("Playback interrupted"))
                if (activeUtterances.isEmpty() && !nativePlayback.isBusy) abandonAudioFocus()
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val id = utteranceId ?: return
                if (id !in activeUtterances || start < 0 || end < start) return
                val epoch = playbackEpoch.get()
                val offset = ssmlTextOffsets[id] ?: 0
                val adjustedStart = (start - offset).coerceAtLeast(0)
                val adjustedEnd = (end - offset).coerceAtLeast(adjustedStart)
                mainHandler.post {
                    if (!closed && playbackEpoch.get() == epoch) onWordHighlightCallback?.invoke(WordHighlight(id, adjustedStart, adjustedEnd))
                }
            }
        })

        isReady = true
        mainHandler.post { if (!closed && isReady) config.onReady?.invoke(this) }
    }

    // -- Simple API --

    private fun handleEngineError(utteranceId: String?, code: Int?) {
        val id = utteranceId ?: return
        if (!activeUtterances.remove(id)) return
        ssmlTextOffsets.remove(id)
        val error = SpeechResult.Error("TTS synthesis failed" + (code?.let { " (code=$it)" } ?: ""), code, id)
        pendingCallbacks.remove(id)?.invoke(error)
        if (activeUtterances.isEmpty() && !nativePlayback.isBusy) abandonAudioFocus()
        mainHandler.post { onSpeechError?.invoke(id); onDetailedSpeechError?.invoke(error) }
    }

    /**
     * Speaks [text] aloud using the given [preset].
     *
     * If [Config.autoChunkLongText] is enabled and the generated SSML exceeds ~4 000 characters,
     * the text is automatically split at natural boundaries (paragraphs, sentences, clauses).
     *
     * @param text The French text to speak.
     * @param preset Prosody preset to apply (rate, pitch, volume). Defaults to [Config.defaultPreset].
     * @param queueMode [TextToSpeech.QUEUE_FLUSH] (default) to interrupt ongoing speech,
     *                   or [TextToSpeech.QUEUE_ADD] to append to the queue.
     * @return [SpeechResult.Success] if the text was dispatched, [SpeechResult.NotReady] if the
     *         engine is not initialized, or [SpeechResult.Error] on failure.
     */
    fun speak(
        text: String,
        preset: SpeechPreset = config.defaultPreset,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()

        val processed = preprocess(text)
        if (config.playbackMode == PlaybackMode.NATIVE) return startNative(wrapInProsody(preset, textToNodes(processed)), queueMode)
        val ssml = SsmlRenderer.render(wrapInProsody(preset, textToNodes(processed)))
        val offset = computeSsmlTextOffset(preset)

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val chunks = renderTextChunks(processed, preset)
            chunks.forEachIndexed { index, chunkSsml ->
                val result = dispatchSsml(chunkSsml, if (index == 0) queueMode else TextToSpeech.QUEUE_ADD, offset)
                if (result != SpeechResult.Success) return result
            }
            return SpeechResult.Success
        }

        return dispatchSsml(ssml, queueMode, offset)
    }

    // -- DSL API --

    /**
     * Speaks content built with the Kotlin DSL.
     *
     * ```kotlin
     * tts.speak {
     *     sentence { text("Première phrase.") }
     *     pause(300)
     *     emphasis("strong") { text("Important !") }
     * }
     * ```
     *
     * @param queueMode [TextToSpeech.QUEUE_FLUSH] (default) or [TextToSpeech.QUEUE_ADD].
     * @param block DSL block executed on a [SpeechBuilder] receiver.
     * @return [SpeechResult] indicating success or failure.
     * @see SpeechBuilder
     */
    fun speak(queueMode: Int = TextToSpeech.QUEUE_FLUSH, block: SpeechBuilder.() -> Unit): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()
        val builder = SpeechBuilder().apply(block)
        if (config.playbackMode == PlaybackMode.NATIVE) return startNative(prepareNativeNodes(builder.nodes), queueMode)
        val ssml = SsmlRenderer.render(builder.nodes)

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val maxContent = 3900 - SPEAK_OVERHEAD
            val groups = SsmlRenderer.chunkNodes(builder.nodes, maxContent)
            groups.forEachIndexed { index, group ->
                val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
                val result = dispatchSsml(SsmlRenderer.render(group), mode)
                if (result != SpeechResult.Success) return result
            }
            return SpeechResult.Success
        }

        return dispatchSsml(ssml, queueMode)
    }

    /**
     * Speaks DSL content wrapped in the given [preset] prosody.
     *
     * This is a convenience method equivalent to wrapping the entire DSL block
     * inside a [SpeechBuilder.withPreset] call.
     *
     * @param preset The [SpeechPreset] whose prosody wraps the DSL content.
     * @param queueMode [TextToSpeech.QUEUE_FLUSH] (default) or [TextToSpeech.QUEUE_ADD].
     * @param block DSL block executed on a [SpeechBuilder] receiver.
     * @return [SpeechResult] indicating success or failure.
     */
    fun speakWithPreset(
        preset: SpeechPreset,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        block: SpeechBuilder.() -> Unit
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()
        val inner = SpeechBuilder().apply(block).nodes
        if (config.playbackMode == PlaybackMode.NATIVE) return startNative(wrapInProsody(preset, prepareNativeNodes(inner)), queueMode)
        val ssml = SsmlRenderer.render(wrapInProsody(preset, inner))

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val maxContent = 3900 - SPEAK_OVERHEAD - computeProsodyOverhead(preset)
            val groups = SsmlRenderer.chunkNodes(inner, maxContent)
            groups.forEachIndexed { index, group ->
                val result = dispatchSsml(SsmlRenderer.render(wrapInProsody(preset, group)), if (index == 0) queueMode else TextToSpeech.QUEUE_ADD)
                if (result != SpeechResult.Success) return result
            }
            return SpeechResult.Success
        }

        return dispatchSsml(ssml, queueMode)
    }

    // -- Coroutines API --

    /**
     * Suspends until the TTS engine finishes speaking [text].
     *
     * The coroutine is cancelled-safe: cancelling the job will stop the ongoing speech
     * and clean up the internal callback.
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     when (val result = tts.speakAndAwait("Bonjour")) {
     *         is SpeechResult.Success -> { /* done */ }
     *         is SpeechResult.Error   -> { /* handle error */ }
     *         SpeechResult.NotReady   -> { /* engine not ready */ }
     *     }
     * }
     * ```
     *
     * @param text The French text to speak.
     * @param preset Prosody preset to apply. Defaults to [Config.defaultPreset].
     * @return [SpeechResult] once the utterance completes or fails.
     */
    suspend fun speakAndAwait(
        text: String,
        preset: SpeechPreset = config.defaultPreset
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()
        val processed = preprocess(text)
        if (config.playbackMode == PlaybackMode.NATIVE) return awaitNative(wrapInProsody(preset, textToNodes(processed)), TextToSpeech.QUEUE_FLUSH)
        val ssml = SsmlRenderer.render(wrapInProsody(preset, textToNodes(processed)))
        val offset = computeSsmlTextOffset(preset)

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val chunks = renderTextChunks(processed, preset)
            for ((index, chunkSsml) in chunks.withIndex()) {
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = awaitUtterance(chunkSsml, mode, offset)
                if (result is SpeechResult.Error) return result
            }
            return SpeechResult.Success
        }

        return awaitUtterance(ssml, TextToSpeech.QUEUE_FLUSH, offset)
    }

    /**
     * Suspends until the TTS engine finishes speaking the DSL content.
     *
     * Combines the [SpeechBuilder] DSL with coroutine suspension for sequential speech flows.
     *
     * @param queueMode [TextToSpeech.QUEUE_FLUSH] (default) or [TextToSpeech.QUEUE_ADD].
     * @param block DSL block executed on a [SpeechBuilder] receiver.
     * @return [SpeechResult] once the utterance completes or fails.
     * @see speakAndAwait
     * @see SpeechBuilder
     */
    suspend fun speakAndAwait(
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        block: SpeechBuilder.() -> Unit
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()
        val nodes = SpeechBuilder().apply(block).nodes
        if (config.playbackMode == PlaybackMode.NATIVE) return awaitNative(prepareNativeNodes(nodes), queueMode)
        val ssml = SsmlRenderer.render(nodes)

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val groups = SsmlRenderer.chunkNodes(nodes, 3900 - SPEAK_OVERHEAD)
            for ((index, group) in groups.withIndex()) {
                val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
                val result = awaitUtterance(SsmlRenderer.render(group), mode)
                if (result is SpeechResult.Error) return result
            }
            return SpeechResult.Success
        }

        return awaitUtterance(ssml, queueMode)
    }

    // -- Synthesize to file --

    /**
     * Renders [text] to a WAV audio file using the given [preset].
     *
     * The file is written asynchronously by the TTS engine. This method returns immediately
     * after dispatching the request.
     *
     * @param text The French text to synthesize.
     * @param file Destination [File] where the audio will be written.
     * @param preset Prosody preset to apply. Defaults to [Config.defaultPreset].
     * @return [SpeechResult.Success] if the request was dispatched, or [SpeechResult.NotReady].
     */
    fun synthesizeToFile(
        text: String,
        file: File,
        preset: SpeechPreset = config.defaultPreset,
    ): SpeechResult = startFileSynthesis(text, file, preset)

    /** Await the engine's file-completion callback. Cancellation stops this export; partial files are retained. */
    suspend fun synthesizeToFileAndAwait(
        text: String,
        file: File,
        preset: SpeechPreset = config.defaultPreset,
    ): SpeechResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            var requestId: String? = null
            continuation.invokeOnCancellation {
                mainHandler.post {
                    val id = requestId
                    if (id != null && pendingCallbacks.containsKey(id)) stop()
                }
            }
            if (continuation.isActive) {
                val result = startFileSynthesis(text, file, preset,
                    onComplete = { if (continuation.isActive) continuation.resume(it) },
                    onRegistered = { requestId = it })
                if (result != SpeechResult.Success && continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun startFileSynthesis(
        text: String,
        file: File,
        preset: SpeechPreset,
        onComplete: ((SpeechResult) -> Unit)? = null,
        onRegistered: (String) -> Unit = {},
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        if (nativePlayback.isBusy || activeUtterances.isNotEmpty()) return SpeechResult.Error("Stop playback before file synthesis or use a separate instance")
        val processed = preprocess(text)
        if (config.playbackMode == PlaybackMode.NATIVE) {
            val plan = try { NativeSpeechPlan.compile(wrapInProsody(preset, textToNodes(processed)), config.normalization.region, false) }
                catch (error: IllegalArgumentException) { return SpeechResult.Error(error.message ?: "Invalid speech") }
            val spoken = plan.joinToString("") { it.text }
            if (spoken.length > 3900) return SpeechResult.Error("File synthesis requires a single bounded text")
            val engine = tts ?: return SpeechResult.NotReady
            val step = plan.firstOrNull() ?: NativeSpeechStep()
            if (engine.setSpeechRate(step.rate) != TextToSpeech.SUCCESS || engine.setPitch(step.pitch) != TextToSpeech.SUCCESS) return SpeechResult.Error("Engine rejected prosody")
            return dispatchFile(spoken, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, step.volume) }, file, onComplete, onRegistered)
        }
        val ssml = SsmlRenderer.render(wrapInProsody(preset, textToNodes(processed)))
        if (ssml.length > MAX_SSML_LENGTH) return SpeechResult.Error("File synthesis exceeds the TTS input limit")
        return dispatchFile(ssml, Bundle(), file, onComplete, onRegistered)
    }

    private fun dispatchFile(text: String, params: Bundle, file: File,
        onComplete: ((SpeechResult) -> Unit)?, onRegistered: (String) -> Unit): SpeechResult {
        val id = nextUtteranceId()
        activeUtterances.add(id)
        if (onComplete != null) pendingCallbacks[id] = onComplete
        onRegistered(id)
        val result = try {
            tts?.synthesizeToFile(text, params, file, id)
        } catch (error: RuntimeException) {
            activeUtterances.remove(id)
            pendingCallbacks.remove(id)
            if (error !is SecurityException && error !is IllegalArgumentException) throw error
            return SpeechResult.Error(error.message ?: "Cannot synthesize to destination", utteranceId = id)
        }
        if (result != TextToSpeech.SUCCESS) { activeUtterances.remove(id); pendingCallbacks.remove(id) }
        return if (result == TextToSpeech.SUCCESS) SpeechResult.Success
        else SpeechResult.Error("TTS synthesizeToFile returned error code: $result", result, id)
    }

    // -- Raw SSML --

    /**
     * Speaks raw SSML directly, bypassing the DSL and preset system.
     *
     * The [ssml] string should be a complete SSML document (with `<speak>` root) or a fragment
     * that the TTS engine can interpret.
     *
     * @param ssml Raw SSML markup to speak.
     * @param queueMode [TextToSpeech.QUEUE_FLUSH] (default) or [TextToSpeech.QUEUE_ADD].
     * @return [SpeechResult] indicating success or failure.
     */
    fun speakSsml(ssml: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        cancelQueueIfActive()
        return dispatchSsml(ssml, queueMode)
    }

    // -- SSML preview (debug) --

    /**
     * Returns the SSML that would be generated by the DSL block, without speaking it.
     *
     * Useful for debugging or logging the generated SSML markup.
     *
     * @param block DSL block executed on a [SpeechBuilder] receiver.
     * @return The generated SSML string (e.g. `<speak><prosody ...>...</prosody></speak>`).
     */
    fun buildSsml(block: SpeechBuilder.() -> Unit): String {
        val builder = SpeechBuilder().apply(block)
        return SsmlRenderer.render(builder.nodes)
    }

    /**
     * Returns the SSML that would be generated for [text] with the given [preset], without speaking it.
     *
     * @param text The French text to wrap in SSML prosody.
     * @param preset Prosody preset to apply. Defaults to [Config.defaultPreset].
     * @return The generated SSML string.
     */
    fun buildSsml(text: String, preset: SpeechPreset = config.defaultPreset): String {
        val processed = preprocess(text)
        return SsmlRenderer.render(wrapInProsody(preset, textToNodes(processed)))
    }

    // -- Voice control --

    /**
     * Returns all available offline French voices on this device, sorted by quality (descending).
     *
     * Can be used to let the user pick a voice manually via [setVoice].
     *
     * @return A list of [Voice] objects, or an empty list if the engine is not ready.
     * @see setVoice
     */
    fun listAvailableVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        return voiceSelector.listFrenchVoices(engine)
    }

    /**
     * Manually sets the active TTS voice.
     *
     * @param voice A [Voice] object, typically obtained from [listAvailableVoices].
     */
    fun setVoice(voice: Voice) {
        trySetVoice(voice)
    }

    fun trySetVoice(voice: Voice): SpeechResult {
        val engine = tts ?: return SpeechResult.NotReady
        if (!isReady) return SpeechResult.NotReady
        if (nativePlayback.isBusy || activeUtterances.isNotEmpty()) return SpeechResult.Error("Stop playback before changing voice")
        if (voice.locale.language != "fr" || config.offlineOnly && (voice.isNetworkConnectionRequired || voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))) return SpeechResult.Error("Voice violates French/offline policy")
        if (config.requireExactLocale && voice.locale != config.locale) return SpeechResult.Error("Voice violates exact locale policy")
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return SpeechResult.Error("Engine rejected voice")
        currentVoice = voice
        return SpeechResult.Success
    }

    fun preview(text: String): FrenchTextPreprocessor.Preview = if (config.preprocessText)
        FrenchTextPreprocessor.preview(text, config.normalization)
    else FrenchTextPreprocessor.Preview(text, text, emptyList())

    /** Preview the configured mode, normalization, dictionary, controls and chunking. No synthesis. */
    fun previewSpeech(text: String, preset: SpeechPreset = config.defaultPreset): SpeechPreview {
        val normalized = preprocess(text)
        val nodes = wrapInProsody(preset, textToNodes(normalized))
        return try {
            if (config.playbackMode == PlaybackMode.NATIVE) SpeechPreview(text, normalized,
                nativeSteps = NativeSpeechPlan.compile(nodes, config.normalization.region, config.autoChunkLongText))
            else SpeechPreview(text, normalized, ssml = SsmlRenderer.render(nodes))
        } catch (error: IllegalArgumentException) {
            SpeechPreview(text, normalized, result = SpeechResult.Error(error.message ?: "Invalid speech"))
        }
    }
    fun exportPronunciations(): String = dictionary.export()
    fun importPronunciations(data: String, replace: Boolean = false) { dictionary.import(data, replace) }

    // -- Pronunciation dictionary --

    /**
     * Adds a pronunciation rule to the dictionary.
     *
     * Each word can only have **one** active rule. Adding a new rule for the same word
     * replaces the previous one, preventing conflicts between alias and IPA.
     *
     * Matching is **case-insensitive**.
     *
     * ```kotlin
     * // Simple alias — TTS reads "Oua-ouei"
     * tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
     *
     * // IPA — exact phonetic control
     * tts.addPronunciation(PronunciationRule.Ipa("Lacoste", "la.kɔst"))
     *
     * // Replaces the alias with IPA for the same word
     * tts.addPronunciation(PronunciationRule.Ipa("Huawei", "wa.wɛj"))
     * ```
     *
     * @param rule The [PronunciationRule] to add (either [PronunciationRule.Alias] or [PronunciationRule.Ipa]).
     * @return This instance for chaining.
     * @see PronunciationRule
     */
    fun addPronunciation(rule: PronunciationRule): BetterFrenchTts {
        dictionary.add(rule)
        return this
    }

    /**
     * Removes a word from the pronunciation dictionary.
     *
     * @param word The word to stop substituting (case-insensitive).
     * @return This instance for chaining.
     */
    fun removePronunciation(word: String): BetterFrenchTts {
        dictionary.remove(word)
        return this
    }

    /**
     * Removes all entries from the pronunciation dictionary.
     *
     * @return This instance for chaining.
     */
    fun clearPronunciations(): BetterFrenchTts {
        dictionary.clear()
        return this
    }

    // -- Speech queue --

    /**
     * Adds a text item to the speech queue.
     *
     * Items are not played until [playQueue] is called. Multiple items can be
     * enqueued before starting playback.
     *
     * ```kotlin
     * tts.enqueue("Bienvenue.")
     * tts.enqueue("Voici les nouvelles.", preset = SpeechPreset.NEWS)
     * tts.playQueue()
     * ```
     *
     * @param text The French text to speak.
     * @param preset Prosody preset for this item. Defaults to [Config.defaultPreset].
     * @return This instance for chaining.
     * @see playQueue
     */
    fun enqueue(text: String, preset: SpeechPreset = config.defaultPreset): BetterFrenchTts {
        synchronized(queueItems) { queueItems += QueueItem.Text(text, preset) }
        return this
    }

    /**
     * Adds a DSL-built speech item to the queue.
     *
     * ```kotlin
     * tts.enqueue {
     *     slow { text("Point important.") }
     *     pause(300)
     *     emphasis { text("Très important !") }
     * }
     * ```
     *
     * @param block DSL block executed on a [SpeechBuilder] receiver.
     * @return This instance for chaining.
     * @see playQueue
     */
    fun enqueue(block: SpeechBuilder.() -> Unit): BetterFrenchTts {
        synchronized(queueItems) { queueItems += QueueItem.Dsl(block) }
        return this
    }

    /**
     * Adds multiple text items to the queue at once, all sharing the same [preset].
     *
     * @param texts The French texts to enqueue.
     * @param preset Prosody preset applied to every item. Defaults to [Config.defaultPreset].
     * @return This instance for chaining.
     * @see enqueue
     */
    fun enqueueAll(texts: List<String>, preset: SpeechPreset = config.defaultPreset): BetterFrenchTts {
        synchronized(queueItems) { texts.forEach { queueItems += QueueItem.Text(it, preset) } }
        return this
    }

    /**
     * Starts playing the speech queue from the beginning.
     *
     * Items are spoken sequentially. Use [onQueueProgress] to track progress and
     * [onQueueFinished] to be notified when all items have been spoken.
     *
     * If a queue is already playing, it is restarted from the first item.
     *
     * @return [SpeechResult.Success] if playback started, [SpeechResult.NotReady] if the engine
     *   is not initialized, or [SpeechResult.Error] if the queue is empty.
     * @see enqueue
     * @see pauseQueue
     * @see skipToNext
     */
    fun playQueue(): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        if (synchronized(queueItems) { queueItems.isEmpty() }) return SpeechResult.Error("Queue is empty")
        queueGeneration.incrementAndGet()
        queuePaused = true
        tts?.stop()
        interruptPlayback()
        synchronized(queueItems) {
            if (queueItems.isEmpty()) return SpeechResult.Error("Queue is empty")
            queueIndex = 0
            queueActive = true
            queuePaused = false
        }
        return dispatchCurrentQueueItem()
    }

    /**
     * Pauses the speech queue.
     *
     * The currently playing utterance is stopped. Call [resumeQueue] to resume
     * from the **same item** (replayed from the start, since Android TTS does not
     * support mid-utterance pause).
     *
     * @return [SpeechResult.Success] if paused, or [SpeechResult.Error] if no queue is active.
     * @see resumeQueue
     */
    fun pauseQueue(): SpeechResult {
        if (!queueActive) return SpeechResult.Error("No queue is active")
        queueGeneration.incrementAndGet()
        queuePaused = true
        tts?.stop()
        interruptPlayback()
        return SpeechResult.Success
    }

    /**
     * Resumes a paused speech queue.
     *
     * The current item is replayed from the beginning.
     *
     * @return [SpeechResult.Success] if playback resumed, or [SpeechResult.Error]
     *   if the queue is not paused.
     * @see pauseQueue
     */
    fun resumeQueue(): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        if (!queueActive || !queuePaused) return SpeechResult.Error("Queue is not paused")
        queuePaused = false
        return dispatchCurrentQueueItem()
    }

    /**
     * Skips to the next item in the queue.
     *
     * The currently playing utterance is stopped and the next item starts immediately.
     * If the current item is the last one, the queue finishes.
     *
     * @return [SpeechResult.Success] if the next item started (or queue finished),
     *   or [SpeechResult.Error] if no queue is active.
     */
    fun skipToNext(): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        if (!queueActive) return SpeechResult.Error("No queue is active")
        queueGeneration.incrementAndGet()
        queuePaused = true
        tts?.stop()
        interruptPlayback()
        queuePaused = false
        synchronized(queueItems) {
            queueIndex++
            if (queueIndex >= queueItems.size) {
                finishQueue()
                return SpeechResult.Success
            }
        }
        return dispatchCurrentQueueItem()
    }

    /**
     * Clears the speech queue and stops any ongoing playback.
     *
     * @return This instance for chaining.
     */
    fun clearQueue(): BetterFrenchTts {
        tts?.stop()
        clearPlaybackState()
        return this
    }

    /**
     * Registers a callback invoked on the **main thread** each time a new queue item
     * starts playing.
     *
     * ```kotlin
     * tts.onQueueProgress { progress ->
     *     Log.d("TTS", "Playing ${progress.currentIndex + 1}/${progress.totalItems}")
     * }
     * ```
     *
     * @param callback Receives a [QueueProgress] with the current position and total count.
     * @return This instance for chaining.
     * @see QueueProgress
     */
    fun onQueueProgress(callback: (QueueProgress) -> Unit): BetterFrenchTts {
        onQueueProgressCallback = callback
        return this
    }

    /**
     * Registers a callback invoked on the **main thread** when all queue items
     * have been spoken.
     *
     * @param callback Called when the queue finishes naturally (not on [clearQueue] or [stop]).
     * @return This instance for chaining.
     */
    fun onQueueFinished(callback: () -> Unit): BetterFrenchTts {
        onQueueFinishedCallback = callback
        return this
    }

    // -- Playback control --

    /**
     * Stops any ongoing speech immediately and clears the playback queue.
     *
     * Pending [speakAndAwait] calls complete with [SpeechResult.Error]. Cancelling a
     * caller's coroutine stops this instance's playback, including queued native jobs.
     */
    fun stop() {
        tts?.stop()
        clearPlaybackState()
    }

    /** `true` if the TTS engine is currently speaking an utterance. */
    val isSpeaking: Boolean get() = tts?.isSpeaking == true

    // -- Callbacks (all dispatched on the main thread) --

    /**
     * Registers a callback invoked on the **main thread** when an utterance starts playing.
     *
     * @param callback Receives the utterance ID.
     * @return This instance for chaining.
     */
    fun onStart(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechStart = callback
        return this
    }

    /**
     * Registers a callback invoked on the **main thread** when an utterance finishes successfully.
     *
     * @param callback Receives the utterance ID.
     * @return This instance for chaining.
     */
    fun onDone(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechDone = callback
        return this
    }

    /**
     * Registers a callback invoked on the **main thread** when an utterance fails.
     *
     * @param callback Receives the utterance ID.
     * @return This instance for chaining.
     */
    fun onError(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechError = callback
        return this
    }

    /** Receives asynchronous synthesis failures with Android code and utterance ID on the main thread. */
    fun onDetailedError(callback: (SpeechResult.Error) -> Unit): BetterFrenchTts {
        onDetailedSpeechError = callback
        return this
    }

    /**
     * Registers a callback invoked on the **main thread** each time the TTS engine
     * starts speaking a new word or text range.
     *
     * For calls made with [speak]\(text\) or [speakAndAwait]\(text\), the [WordHighlight.start]
     * and [WordHighlight.end] positions have their outer wrapper offset removed.
     * Preprocessing, escaping and chunking can still change source positions; validate
     * these ranges against the displayed text before using them for highlighting.
     *
     * When speech finishes, a final [WordHighlight] with `start = -1` and `end = -1` is emitted
     * to signal that highlighting should be cleared.
     *
     * Native positions refer to the current spoken segment; SSML positions refer to markup.
     * Neither mode promises an exact map to the original source string.
     *
     * @param callback Receives a [WordHighlight] with the currently spoken range.
     * @return This instance for chaining.
     * @see WordHighlight
     */
    fun onWordHighlight(callback: (WordHighlight) -> Unit): BetterFrenchTts {
        onWordHighlightCallback = callback
        return this
    }

    // -- Lifecycle --

    /**
     * Releases all TTS resources. **Must** be called when the instance is no longer needed
     * (e.g. in `Activity.onDestroy()` or a Compose `DisposableEffect`).
     *
     * After calling this method, [isInitialized] returns `false` and all pending callbacks
     * are cleared.
     */
    fun shutdown() {
        closed = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        currentVoice = null
        clearPlaybackState()
    }

    // -- Internal: queue --

    private fun dispatchCurrentQueueItem(): SpeechResult {
        val generation = queueGeneration.get()
        val item: QueueItem
        val position: Int
        val total: Int
        synchronized(queueItems) {
            if (queueIndex < 0 || queueIndex >= queueItems.size) {
                finishQueue()
                return SpeechResult.Success
            }
            item = queueItems[queueIndex]
            position = queueIndex
            total = queueItems.size
        }

        mainHandler.post {
            if (queueGeneration.get() == generation && queueActive) onQueueProgressCallback?.invoke(QueueProgress(position, total))
        }

        val onItemDone: (SpeechResult) -> Unit = { result ->
            if (queueGeneration.get() == generation && queueActive && !queuePaused) {
                if (result == SpeechResult.Success) advanceQueue() else { resetQueueState(); tts?.stop(); interruptPlayback() }
            }
        }

        val result = try { when (item) {
            is QueueItem.Text -> dispatchQueueTextItem(item, onItemDone)
            is QueueItem.Dsl -> {
                val nodes = SpeechBuilder().apply(item.block).nodes
                if (config.playbackMode == PlaybackMode.NATIVE) startNative(prepareNativeNodes(nodes), TextToSpeech.QUEUE_FLUSH, onItemDone)
                else dispatchSsml(SsmlRenderer.render(nodes), TextToSpeech.QUEUE_FLUSH, onComplete = onItemDone)
            }
        } } catch (error: IllegalArgumentException) { SpeechResult.Error(error.message ?: "Invalid queued speech") }
        if (result != SpeechResult.Success) resetQueueState()
        return result
    }

    private fun dispatchQueueTextItem(item: QueueItem.Text, onItemDone: (SpeechResult) -> Unit): SpeechResult {
        val processed = preprocess(item.text)
        val preset = item.preset
        if (config.playbackMode == PlaybackMode.NATIVE) return startNative(wrapInProsody(preset, textToNodes(processed)), TextToSpeech.QUEUE_FLUSH, onItemDone)
        val ssml = SsmlRenderer.render(wrapInProsody(preset, textToNodes(processed)))
        val offset = computeSsmlTextOffset(preset)

        if (config.autoChunkLongText && ssml.length > MAX_SSML_LENGTH) {
            val chunks = renderTextChunks(processed, preset)
            var lastResult: SpeechResult = SpeechResult.Success
            chunks.forEachIndexed { index, chunkSsml ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                lastResult = dispatchSsml(chunkSsml, mode, offset) { result ->
                    if (result != SpeechResult.Success || index == chunks.lastIndex) onItemDone(result)
                }
                if (lastResult != SpeechResult.Success) return lastResult
            }
            return lastResult
        }

        return dispatchSsml(ssml, TextToSpeech.QUEUE_FLUSH, offset, onItemDone)
    }

    private fun advanceQueue() {
        synchronized(queueItems) {
            queueIndex++
            if (queueIndex >= queueItems.size) {
                finishQueue()
                return
            }
        }
        dispatchCurrentQueueItem()
    }

    private fun finishQueue() {
        resetQueueState()
        val generation = queueGeneration.get()
        abandonAudioFocus()
        mainHandler.post { if (queueGeneration.get() == generation && !queueActive) onQueueFinishedCallback?.invoke() }
    }

    private fun resetQueueState() {
        queueGeneration.incrementAndGet()
        queueActive = false
        queuePaused = false
        queueIndex = -1
        synchronized(queueItems) { queueItems.clear() }
    }

    private fun cancelQueueIfActive() {
        if (queueActive) resetQueueState()
    }

    private fun clearPlaybackState() {
        resetQueueState()
        interruptPlayback()
    }

    private fun interruptPlayback() {
        playbackEpoch.incrementAndGet()
        nativePlayback.cancel()
        activeUtterances.clear()
        ssmlTextOffsets.clear()
        val callbacks = pendingCallbacks.values.toList()
        pendingCallbacks.clear()
        abandonAudioFocus()
        callbacks.forEach { it(SpeechResult.Error("Playback stopped")) }
    }

    // -- Internal --

    companion object {
        private const val SPEAK_OVERHEAD = 15 // "<speak></speak>".length
        private const val MAX_SSML_LENGTH = 4000
        private val utteranceCounter = AtomicLong(0)
        private fun nextUtteranceId(): String = "bftts-${utteranceCounter.incrementAndGet()}"
    }

    private fun wrapInProsody(preset: SpeechPreset, children: List<SsmlNode>): List<SsmlNode> {
        return listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume, children = children))
    }

    private fun renderTextChunks(text: String, preset: SpeechPreset): List<String> {
        return TextChunker.chunk(text).flatMap { chunk ->
            SsmlRenderer.chunkNodes(wrapInProsody(preset, textToNodes(chunk)), 3900 - SPEAK_OVERHEAD)
                .map(SsmlRenderer::render)
        }
    }

    private fun computeSsmlTextOffset(preset: SpeechPreset): Int {
        return SsmlRenderer.render(wrapInProsody(preset, emptyList())).length - "</prosody></speak>".length
    }

    private fun computeProsodyOverhead(preset: SpeechPreset): Int {
        return SsmlRenderer.render(wrapInProsody(preset, emptyList())).length - SPEAK_OVERHEAD
    }

    private fun preprocess(text: String): String {
        return if (config.preprocessText) FrenchTextPreprocessor.process(text, config.normalization) else text
    }

    private fun textToNodes(text: String): List<SsmlNode> = dictionary.nodes(text)

    private fun prepareNativeNodes(nodes: List<SsmlNode>): List<SsmlNode> = nodes.flatMap { node ->
        when (node) {
            is SsmlNode.Text -> textToNodes(preprocess(node.content))
            is SsmlNode.Prosody -> listOf(node.copy(children = prepareNativeNodes(node.children)))
            is SsmlNode.Emphasis -> listOf(node.copy(children = prepareNativeNodes(node.children)))
            is SsmlNode.Paragraph -> listOf(node.copy(children = prepareNativeNodes(node.children)))
            is SsmlNode.Sentence -> listOf(node.copy(children = prepareNativeNodes(node.children)))
            else -> listOf(node)
        }
    }

    private fun startNative(nodes: List<SsmlNode>, queueMode: Int, done: (SpeechResult) -> Unit = {}): SpeechResult {
        val steps = try { NativeSpeechPlan.compile(nodes, config.normalization.region, config.autoChunkLongText) }
        catch (error: IllegalArgumentException) { return SpeechResult.Error(error.message ?: "Invalid native speech") }
        if (queueMode == TextToSpeech.QUEUE_FLUSH) { tts?.stop(); interruptPlayback() }
        return nativePlayback.enqueue(steps) { result ->
            done(result)
            if (!nativePlayback.isBusy && activeUtterances.isEmpty()) abandonAudioFocus()
        }
    }

    private suspend fun awaitNative(nodes: List<SsmlNode>, queueMode: Int): SpeechResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { stop() }
        if (continuation.isActive) {
            val result = startNative(nodes, queueMode) { if (continuation.isActive) continuation.resume(it) }
            if (result != SpeechResult.Success && continuation.isActive) continuation.resume(result)
        }
    }

    private fun dispatchNativeStep(step: NativeSpeechStep, complete: (SpeechResult) -> Unit): SpeechResult {
        val engine = tts ?: return SpeechResult.NotReady
        if (!isReady) return SpeechResult.NotReady
        if (step.silenceMs == 0L && (engine.setSpeechRate(step.rate) != TextToSpeech.SUCCESS || engine.setPitch(step.pitch) != TextToSpeech.SUCCESS)) return SpeechResult.Error("Engine rejected speech controls")
        if (!requestAudioFocus()) return SpeechResult.Error("Audio focus denied")
        val id = nextUtteranceId()
        activeUtterances.add(id)
        pendingCallbacks[id] = complete
        val result = if (step.silenceMs > 0) engine.playSilentUtterance(step.silenceMs, TextToSpeech.QUEUE_ADD, id)
        else engine.speak(step.text, TextToSpeech.QUEUE_ADD, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, step.volume) }, id)
        if (result == TextToSpeech.SUCCESS) return SpeechResult.Success
        activeUtterances.remove(id)
        pendingCallbacks.remove(id)
        if (activeUtterances.isEmpty()) abandonAudioFocus()
        return SpeechResult.Error("Native synthesis rejected: $result", result, id)
    }

    private fun dispatchSsml(
        ssml: String,
        queueMode: Int,
        textOffset: Int = 0,
        onComplete: ((SpeechResult) -> Unit)? = null
    ): SpeechResult {
        if (ssml.length > MAX_SSML_LENGTH) return SpeechResult.Error("Rendered speech exceeds the TTS input limit")
        if (queueMode == TextToSpeech.QUEUE_FLUSH) { tts?.stop(); interruptPlayback() }
        if (!requestAudioFocus()) return SpeechResult.Error("Audio focus denied")
        val utteranceId = nextUtteranceId()
        activeUtterances.add(utteranceId)
        if (textOffset > 0) ssmlTextOffsets[utteranceId] = textOffset
        if (onComplete != null) pendingCallbacks[utteranceId] = onComplete
        val result = tts?.speak(ssml, queueMode, Bundle(), utteranceId)
        return if (result == TextToSpeech.SUCCESS) SpeechResult.Success
        else {
            activeUtterances.remove(utteranceId)
            ssmlTextOffsets.remove(utteranceId)
            pendingCallbacks.remove(utteranceId)
            if (activeUtterances.isEmpty()) abandonAudioFocus()
            SpeechResult.Error("TTS speak returned error code: $result", result, utteranceId)
        }
    }

    private suspend fun awaitUtterance(ssml: String, queueMode: Int, textOffset: Int = 0): SpeechResult {
        if (ssml.length > MAX_SSML_LENGTH) return SpeechResult.Error("Rendered speech exceeds the TTS input limit")
        if (queueMode == TextToSpeech.QUEUE_FLUSH) { tts?.stop(); interruptPlayback() }
        if (!requestAudioFocus()) return SpeechResult.Error("Audio focus denied")
        return suspendCancellableCoroutine { cont ->
            val utteranceId = nextUtteranceId()
            activeUtterances.add(utteranceId)
            if (textOffset > 0) ssmlTextOffsets[utteranceId] = textOffset
            pendingCallbacks[utteranceId] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation {
                activeUtterances.remove(utteranceId)
                ssmlTextOffsets.remove(utteranceId)
                pendingCallbacks.remove(utteranceId)
                stop()
                if (activeUtterances.isEmpty()) abandonAudioFocus()
            }
            val result = tts?.speak(ssml, queueMode, Bundle(), utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                activeUtterances.remove(utteranceId)
                ssmlTextOffsets.remove(utteranceId)
                pendingCallbacks.remove(utteranceId)?.invoke(SpeechResult.Error("TTS speak returned error code: $result", result, utteranceId))
                if (activeUtterances.isEmpty()) abandonAudioFocus()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (config.audioFocus == AudioFocusMode.NONE || hasAudioFocus) return true

        val focusGain = when (config.audioFocus) {
            AudioFocusMode.DUCK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioFocusMode.GAIN_TRANSIENT -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            AudioFocusMode.NONE -> return true
        }

        lateinit var request: AudioFocusRequest
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (audioFocusRequest === request && !closed && change < 0) {
                    if (config.focusLossBehavior == FocusLossBehavior.PAUSE_QUEUE && change != AudioManager.AUDIOFOCUS_LOSS && queueActive) pauseQueue()
                    else if (config.focusLossBehavior != FocusLossBehavior.IGNORE) stop()
                    else abandonAudioFocus()
                }
        }
        request = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(speechAudioAttributes)
            .setWillPauseWhenDucked(config.focusLossBehavior != FocusLossBehavior.IGNORE)
            .setOnAudioFocusChangeListener(listener, mainHandler)
            .build()

        audioFocusRequest = request
        hasAudioFocus = focusAccess.request(request, listener) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) audioFocusRequest = null
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioFocusRequest?.let { focusAccess.abandon(it) }
        audioFocusRequest = null
        hasAudioFocus = false
    }
}
