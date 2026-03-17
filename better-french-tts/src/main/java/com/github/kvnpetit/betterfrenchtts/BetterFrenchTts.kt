package com.github.kvnpetit.betterfrenchtts

import android.content.Context
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

    val isInitialized: Boolean get() = isReady
    var currentVoice: Voice? = null
        private set

    /**
     * Configuration for [BetterFrenchTts].
     *
     * @param defaultPreset Preset applied to every [speak] call when none is specified.
     * @param preferredVoiceNames Ordered list of preferred offline voice names.
     * @param autoChunkLongText Automatically split text longer than ~4000 chars.
     * @param onReady Called on the main thread once the TTS engine is initialized.
     * @param onInitError Called on the main thread if TTS initialization fails.
     */
    data class Config(
        val defaultPreset: SpeechPreset = SpeechPreset.NEUTRAL,
        val preferredVoiceNames: List<String> = FrenchVoiceSelector.DEFAULT_PREFERRED_VOICES,
        val autoChunkLongText: Boolean = true,
        val onReady: ((BetterFrenchTts) -> Unit)? = null,
        val onInitError: ((Int) -> Unit)? = null,
    )

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
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Success)
                mainHandler.post { onSpeechDone?.invoke(id) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: "unknown"
                pendingCallbacks.remove(id)?.invoke(SpeechResult.Error(id))
                mainHandler.post { onSpeechError?.invoke(id) }
            }
        })

        isReady = true
        mainHandler.post { config.onReady?.invoke(this) }
    }

    // -- Simple API --

    /** Speaks [text] using the given [preset]. Returns immediately with a [SpeechResult]. */
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

        if (config.autoChunkLongText && ssml.length > 4000) {
            val chunks = TextChunker.chunk(text)
            chunks.forEachIndexed { index, chunk ->
                val chunkSsml = SsmlRenderer.render(
                    listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                        children = listOf(SsmlNode.Text(chunk))))
                )
                val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
                dispatchSsml(chunkSsml, mode)
            }
            return SpeechResult.Success
        }

        return dispatchSsml(ssml, queueMode)
    }

    // -- DSL API --

    /** Speaks content built with the Kotlin DSL. */
    fun speak(queueMode: Int = TextToSpeech.QUEUE_FLUSH, block: SpeechBuilder.() -> Unit): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        val builder = SpeechBuilder().apply(block)
        return dispatchSsml(SsmlRenderer.render(builder.nodes), queueMode)
    }

    /** Speaks DSL content wrapped in the given [preset] prosody. */
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

    /** Suspends until the TTS engine finishes speaking [text]. */
    suspend fun speakAndAwait(
        text: String,
        preset: SpeechPreset = config.defaultPreset
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        return suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            pendingCallbacks[utteranceId] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation {
                pendingCallbacks.remove(utteranceId)
                tts?.stop()
            }
            val ssml = SsmlRenderer.render(
                listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                    children = listOf(SsmlNode.Text(text))))
            )
            val params = Bundle()
            tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    /** Suspends until the TTS engine finishes speaking the DSL content. */
    suspend fun speakAndAwait(
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        block: SpeechBuilder.() -> Unit
    ): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        return suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            pendingCallbacks[utteranceId] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation {
                pendingCallbacks.remove(utteranceId)
                tts?.stop()
            }
            val builder = SpeechBuilder().apply(block)
            val params = Bundle()
            tts?.speak(SsmlRenderer.render(builder.nodes), queueMode, params, utteranceId)
        }
    }

    // -- Synthesize to file --

    /** Renders [text] to an audio file using the given [preset]. */
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

    /** Speaks raw SSML directly. */
    fun speakSsml(ssml: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): SpeechResult {
        if (!isReady) return SpeechResult.NotReady
        return dispatchSsml(ssml, queueMode)
    }

    // -- SSML preview (debug) --

    /** Returns the SSML that would be generated by the DSL block, without speaking it. */
    fun buildSsml(block: SpeechBuilder.() -> Unit): String {
        val builder = SpeechBuilder().apply(block)
        return SsmlRenderer.render(builder.nodes)
    }

    /** Returns the SSML that would be generated for [text] with the given [preset]. */
    fun buildSsml(text: String, preset: SpeechPreset = config.defaultPreset): String {
        return SsmlRenderer.render(
            listOf(SsmlNode.Prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume,
                children = listOf(SsmlNode.Text(text))))
        )
    }

    // -- Voice control --

    /** Returns all available offline French voices, sorted by quality (descending). */
    fun listAvailableVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        return FrenchVoiceSelector(config.preferredVoiceNames).listFrenchVoices(engine)
    }

    /** Manually sets the active TTS voice. */
    fun setVoice(voice: Voice) {
        tts?.voice = voice
        currentVoice = voice
    }

    // -- Playback control --

    /** Stops any ongoing speech and clears the queue. */
    fun stop() {
        tts?.stop()
        pendingCallbacks.clear()
    }

    val isSpeaking: Boolean get() = tts?.isSpeaking == true

    // -- Callbacks (all dispatched on the main thread) --

    fun onStart(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechStart = callback
        return this
    }

    fun onDone(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechDone = callback
        return this
    }

    fun onError(callback: (String) -> Unit): BetterFrenchTts {
        onSpeechError = callback
        return this
    }

    // -- Lifecycle --

    /** Releases TTS resources. Must be called when done (e.g. in onDestroy / DisposableEffect). */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingCallbacks.clear()
    }

    // -- Internal --

    private fun dispatchSsml(ssml: String, queueMode: Int): SpeechResult {
        val params = Bundle()
        val utteranceId = UUID.randomUUID().toString()
        val result = tts?.speak(ssml, queueMode, params, utteranceId)
        return if (result == TextToSpeech.SUCCESS) SpeechResult.Success
        else SpeechResult.Error("TTS speak returned error code: $result")
    }
}
