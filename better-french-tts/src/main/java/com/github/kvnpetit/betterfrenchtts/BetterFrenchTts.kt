package com.github.kvnpetit.betterfrenchtts

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
import com.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder
import com.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import com.github.kvnpetit.betterfrenchtts.ssml.SsmlRenderer
import com.github.kvnpetit.betterfrenchtts.voice.FrenchVoiceSelector
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * High-level French TTS wrapper around Android's native [TextToSpeech].
 *
 * Automatically selects the best offline French voice, generates SSML behind the scenes,
 * and exposes a Kotlin DSL for fine-grained speech control.
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
class BetterFrenchTts(
    context: Context,
    private val config: Config = Config()
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = ConcurrentHashMap<String, (SpeechResult) -> Unit>()

    private var onSpeechStart: ((String) -> Unit)? = null
    private var onSpeechDone: ((String) -> Unit)? = null
    private var onSpeechError: ((String) -> Unit)? = null
    private var onWordHighlightCallback: ((WordHighlight) -> Unit)? = null

    private val ssmlTextOffsets = ConcurrentHashMap<String, Int>()

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private val activeUtterances = ConcurrentHashMap.newKeySet<String>()

    /** `true` once the TTS engine has been initialized and a French voice has been selected. */
    val isInitialized: Boolean get() = isReady

    /** The currently active [Voice], or `null` if the engine is not yet ready or no voice was found. */
    var currentVoice: Voice? = null
        private set

    /**
     * Configuration for [BetterFrenchTts].
     *
     * @property defaultPreset Preset applied to every [speak] call when none is specified.
     * @property preferredVoiceNames Ordered list of preferred offline voice names.
     *   Voices are matched case-insensitively among the highest-quality candidates.
     * @property autoChunkLongText When `true` (default), text whose SSML exceeds ~4 000 characters
     *   is automatically split at natural boundaries before dispatching.
     * @property audioFocus Audio focus strategy used while speaking.
     *   Defaults to [AudioFocusMode.DUCK] which lowers other apps' volume during speech.
     * @property onReady Called on the **main thread** once the TTS engine is initialized.
     *   Receives the fully ready [BetterFrenchTts] instance.
     * @property onInitError Called on the **main thread** if TTS initialization fails.
     *   Receives the error status code from [android.speech.tts.TextToSpeech.OnInitListener].
     */
    data class Config(
        val defaultPreset: SpeechPreset = SpeechPreset.NEUTRAL,
        val preferredVoiceNames: List<String> = FrenchVoiceSelector.DEFAULT_PREFERRED_VOICES,
        val autoChunkLongText: Boolean = true,
        val audioFocus: AudioFocusMode = AudioFocusMode.DUCK,
        val onReady: ((BetterFrenchTts) -> Unit)? = null,
        val onInitError: ((Int) -> Unit)? = null,
    )

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
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setup()
            } else {
                mainHandler.post { config.onInitError?.invoke(status) }
            }
        }
    }

    private fun setup() {
        val engine = tts ?: return
        engine.language = Locale.FRANCE

        val selector = FrenchVoiceSelector(config.preferredVoiceNames)
        val bestVoice = selector.selectBestVoice(engine)
        if (bestVoice != null) {
            engine.voice = bestVoice
            currentVoice = bestVoice
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeechStart?.invoke(utteranceId ?: "") }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: ""
                activeUtterances.remove(id)
                ssmlTextOffsets.remove(id)
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Success)
                if (activeUtterances.isEmpty()) abandonAudioFocus()
                mainHandler.post {
                    onWordHighlightCallback?.invoke(WordHighlight(id, -1, -1))
                    onSpeechDone?.invoke(id)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: "unknown"
                activeUtterances.remove(id)
                ssmlTextOffsets.remove(id)
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Error(id))
                if (activeUtterances.isEmpty()) abandonAudioFocus()
                mainHandler.post { onSpeechError?.invoke(id) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val id = utteranceId ?: return
                val offset = ssmlTextOffsets[id] ?: 0
                val adjustedStart = (start - offset).coerceAtLeast(0)
                val adjustedEnd = (end - offset).coerceAtLeast(adjustedStart)
                mainHandler.post {
                    onWordHighlightCallback?.invoke(WordHighlight(id, adjustedStart, adjustedEnd))
                }
            }
        })

        isReady = true
        mainHandler.post { config.onReady?.invoke(this) }
    }

    // -- Simple API --

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

        val ssml = SsmlRenderer.render(
            listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                children = listOf(SsmlNode.Text(text))))
        )

        val offset = computeSsmlTextOffset(preset)

        if (config.autoChunkLongText && ssml.length > 4000) {
            val chunks = TextChunker.chunk(text)
            chunks.forEachIndexed { index, chunk ->
                val chunkSsml = SsmlRenderer.render(
                    listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                        children = listOf(SsmlNode.Text(chunk))))
                )
                val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
                dispatchSsml(chunkSsml, mode, offset)
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
        val builder = SpeechBuilder().apply(block)
        return dispatchSsml(SsmlRenderer.render(builder.nodes), queueMode)
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
        val inner = SpeechBuilder().apply(block).nodes
        val wrapped = listOf(
            SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume, children = inner)
        )
        return dispatchSsml(SsmlRenderer.render(wrapped), queueMode)
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
        requestAudioFocus()
        return suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            activeUtterances.add(utteranceId)
            ssmlTextOffsets[utteranceId] = computeSsmlTextOffset(preset)
            pendingCallbacks[utteranceId] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation {
                activeUtterances.remove(utteranceId)
                ssmlTextOffsets.remove(utteranceId)
                pendingCallbacks.remove(utteranceId)
                tts?.stop()
                if (activeUtterances.isEmpty()) abandonAudioFocus()
            }
            val ssml = SsmlRenderer.render(
                listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                    children = listOf(SsmlNode.Text(text))))
            )
            val params = Bundle()
            tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
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
        requestAudioFocus()
        return suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            activeUtterances.add(utteranceId)
            pendingCallbacks[utteranceId] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation {
                activeUtterances.remove(utteranceId)
                pendingCallbacks.remove(utteranceId)
                tts?.stop()
                if (activeUtterances.isEmpty()) abandonAudioFocus()
            }
            val builder = SpeechBuilder().apply(block)
            val params = Bundle()
            tts?.speak(SsmlRenderer.render(builder.nodes), queueMode, params, utteranceId)
        }
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
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        val ssml = SsmlRenderer.render(
            listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                children = listOf(SsmlNode.Text(text))))
        )
        val params = Bundle()
        val utteranceId = UUID.randomUUID().toString()
        tts?.synthesizeToFile(ssml, params, file, utteranceId)
        return SpeechResult.Success
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
        return SsmlRenderer.render(
            listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                children = listOf(SsmlNode.Text(text))))
        )
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
        return FrenchVoiceSelector(config.preferredVoiceNames).listFrenchVoices(engine)
    }

    /**
     * Manually sets the active TTS voice.
     *
     * @param voice A [Voice] object, typically obtained from [listAvailableVoices].
     */
    fun setVoice(voice: Voice) {
        tts?.voice = voice
        currentVoice = voice
    }

    // -- Playback control --

    /**
     * Stops any ongoing speech immediately and clears the playback queue.
     *
     * All pending [speakAndAwait] coroutine callbacks are also discarded. If you need
     * to react to a manual stop, check the coroutine's cancellation state instead.
     */
    fun stop() {
        tts?.stop()
        activeUtterances.clear()
        ssmlTextOffsets.clear()
        pendingCallbacks.clear()
        abandonAudioFocus()
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

    /**
     * Registers a callback invoked on the **main thread** each time the TTS engine
     * starts speaking a new word or text range.
     *
     * For calls made with [speak]\(text\) or [speakAndAwait]\(text\), the [WordHighlight.start]
     * and [WordHighlight.end] positions are mapped back to the **original text** so they can
     * be used directly for UI highlighting.
     *
     * When speech finishes, a final [WordHighlight] with `start = -1` and `end = -1` is emitted
     * to signal that highlighting should be cleared.
     *
     * For DSL-based calls, positions refer to the generated SSML string and may not map
     * to any single source string.
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        activeUtterances.clear()
        ssmlTextOffsets.clear()
        pendingCallbacks.clear()
        abandonAudioFocus()
    }

    // -- Internal --

    private fun dispatchSsml(ssml: String, queueMode: Int, textOffset: Int = 0): SpeechResult {
        requestAudioFocus()
        val params = Bundle()
        val utteranceId = UUID.randomUUID().toString()
        activeUtterances.add(utteranceId)
        if (textOffset > 0) ssmlTextOffsets[utteranceId] = textOffset
        val result = tts?.speak(ssml, queueMode, params, utteranceId)
        return if (result == TextToSpeech.SUCCESS) SpeechResult.Success
        else {
            activeUtterances.remove(utteranceId)
            ssmlTextOffsets.remove(utteranceId)
            if (activeUtterances.isEmpty()) abandonAudioFocus()
            SpeechResult.Error("TTS speak returned error code: $result")
        }
    }

    /**
     * Computes the character offset of the text content inside the SSML wrapper
     * generated for a simple `speak(text, preset)` call.
     *
     * For example, `<speak><prosody rate="medium" pitch="+0st" volume="medium">` has a
     * known length that can be subtracted from `onRangeStart` positions.
     */
    private fun computeSsmlTextOffset(preset: SpeechPreset): Int {
        // Mirrors SsmlRenderer: <speak><prosody rate="..." pitch="..." volume="...">
        val attrs = listOf(
            "rate=\"${preset.rate}\"",
            "pitch=\"${preset.pitch}\"",
            "volume=\"${preset.volume}\""
        ).joinToString(" ")
        return "<speak><prosody $attrs>".length
    }

    private fun requestAudioFocus() {
        if (config.audioFocus == AudioFocusMode.NONE || hasAudioFocus) return

        val focusGain = when (config.audioFocus) {
            AudioFocusMode.DUCK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioFocusMode.GAIN_TRANSIENT -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            AudioFocusMode.NONE -> return
        }

        val request = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* no-op: we don't pause on focus loss */ }
            .build()

        audioFocusRequest = request
        hasAudioFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        hasAudioFocus = false
    }
}
