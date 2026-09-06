package io.github.kvnpetit.betterfrenchtts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Exercises the wrapper against a controlled engine, without downloaded voices. */
@RunWith(AndroidJUnit4::class)
class EngineContractTest {
    private class Focus : AudioFocusAccess {
        var granted = true
        var requests = 0
        var abandoned = 0
        lateinit var last: AudioFocusRequest
        lateinit var listener: AudioManager.OnAudioFocusChangeListener
        override fun request(request: AudioFocusRequest, listener: AudioManager.OnAudioFocusChangeListener): Int {
            last = request; this.listener = listener; requests++
            return if (granted) AudioManager.AUDIOFOCUS_REQUEST_GRANTED else AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        override fun abandon(request: AudioFocusRequest) { abandoned++ }
        fun lose(change: Int) { InstrumentationRegistry.getInstrumentation().runOnMainSync { listener.onAudioFocusChange(change) } }
    }

    @Test fun deniedFocusDoesNotSendSpeechInEitherMode() {
        for (mode in BetterFrenchTts.PlaybackMode.entries) {
            val focus = Focus().apply { granted = false }
            fixture(BetterFrenchTts.Config(playbackMode = mode), focusAccess = focus) { speech, engine ->
                engine.response = TextToSpeech.SUCCESS
                assertTrue(speech.speak("Bonjour") is SpeechResult.Error)
                assertTrue(engine.requests.isEmpty())
            }
        }
    }
    @Test fun nativeSegmentsKeepOneFocusLeaseUntilCompletion() {
        val focus = Focus()
        fixture(BetterFrenchTts.Config(), focusAccess = focus) { speech, engine ->
            engine.response = TextToSpeech.SUCCESS
            speech.speak { text("a"); pause(20); text("b") }
            repeat(3) { engine.listener.onDone(engine.ids.last()); drain() }
            assertEquals(1, focus.requests)
            assertEquals(1, focus.abandoned)
            assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, engine.attributes!!.contentType)
            assertEquals(engine.attributes, focus.last.audioAttributes)
        }
    }
    @Test fun focusLossStopsWithoutAutomaticRestartAndIgnoresStaleEvents() {
        val focus = Focus()
        fixture(BetterFrenchTts.Config(), focusAccess = focus) { speech, engine ->
            engine.response = TextToSpeech.SUCCESS
            runBlocking {
                val pending = async(start = CoroutineStart.UNDISPATCHED) { speech.speakAndAwait("bonjour") }
                val oldListener = focus.listener
                focus.lose(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                assertTrue(withTimeout(3000) { pending.await() } is SpeechResult.Error)
                val before = engine.stops
                speech.speak("nouveau")
                val after = engine.stops
                InstrumentationRegistry.getInstrumentation().runOnMainSync { oldListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS) }
                assertEquals(after, engine.stops)
                assertTrue(after > before)
            }
        }
    }
    @Test fun transientFocusLossCanPreserveQueueForManualResume() {
        val focus = Focus()
        fixture(BetterFrenchTts.Config(focusLossBehavior = BetterFrenchTts.FocusLossBehavior.PAUSE_QUEUE), focusAccess = focus) { speech, engine ->
            engine.response = TextToSpeech.SUCCESS
            speech.enqueue("a").enqueue("b").playQueue()
            focus.lose(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertTrue(speech.isQueuePaused)
            assertEquals(2, speech.queueSize)
            assertEquals(SpeechResult.Success, speech.resumeQueue())
            assertEquals(listOf("a", "a"), engine.requests)
        }
    }
    @Test fun lateStartAndRangeCallbacksAfterStopAreIgnored() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        var callbacks = 0
        speech.onStart { callbacks++ }.onWordHighlight { callbacks++ }
        speech.speak("bonjour")
        val id = engine.ids.last()
        speech.stop()
        engine.listener.onStart(id)
        engine.listener.onRangeStart(id, 0, 3, 0)
        drain()
        assertEquals(0, callbacks)
    }
    @Test fun fileExportCannotChangeActivePlaybackControls() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        speech.speak("bonjour")
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "busy.wav")
        assertTrue(speech.synthesizeToFile("export", file) is SpeechResult.Error)
        assertEquals(listOf("bonjour"), engine.requests)
    }
    @Test fun fileCompletionIsDeliveredToCaller() {
        fixture(BetterFrenchTts.Config(audioFocus = BetterFrenchTts.AudioFocusMode.NONE)) { speech, engine ->
            engine.response = TextToSpeech.SUCCESS
            var completed = false
            speech.onDone { completed = true }
            val destination = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "contract-output.wav")
            assertEquals(SpeechResult.Success, speech.synthesizeToFile("Bonjour", destination))
            engine.listener.onDone(engine.ids.last())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(completed)
        }
    }
    @Test fun exactFrancePolicyRejectsRegionalFallback() {
        fixture(BetterFrenchTts.Config(audioFocus = BetterFrenchTts.AudioFocusMode.NONE, requireExactLocale = true),
            prepare = { it.offeredVoices = setOf(Voice("ca-local", Locale.CANADA_FRENCH, 500, 100, false, emptySet())) }) { speech, _ ->
            assertFalse(speech.isInitialized)
            assertNull(speech.currentVoice)
        }
    }

    @Test fun disabledNormalizationPreviewPreservesInput() {
        fixture(BetterFrenchTts.Config(preprocessText = false, audioFocus = BetterFrenchTts.AudioFocusMode.NONE)) { speech, _ ->
            assertEquals("1h", speech.preview("1h").text)
            assertTrue(speech.preview("1h").transformations.isEmpty())
        }
    }

    @Test fun rejectedSsmlQueueDoesNotRemainActive() {
        fixture { speech, _ ->
            speech.enqueue("Bonjour")
            assertTrue(speech.playQueue() is SpeechResult.Error)
            assertFalse(speech.isQueuePlaying)
        }
    }

    private class Engine(context: Context) : TextToSpeech(context, null) {
        val requests = mutableListOf<String>()
        var response = ERROR
        var offeredVoices = setOf(Voice("fr-local", Locale.FRANCE, 400, 100, false, emptySet()))
        var voiceResult = SUCCESS
        var selectedVoice: Voice? = null
        var languageResult = LANG_AVAILABLE
        var appliedRate = 1f
        var appliedPitch = 1f
        var attributes: AudioAttributes? = null
        var stops = 0
        val rates = mutableListOf<Float>()
        val ids = mutableListOf<String>()
        val silences = mutableListOf<Long>()
        lateinit var listener: UtteranceProgressListener
        override fun setLanguage(locale: Locale): Int = languageResult
        override fun getVoices(): Set<Voice> = offeredVoices
        override fun setVoice(voice: Voice): Int { if (voiceResult == SUCCESS) selectedVoice = voice; return voiceResult }
        override fun setSpeechRate(rate: Float): Int { appliedRate = rate; return SUCCESS }
        override fun setPitch(pitch: Float): Int { appliedPitch = pitch; return SUCCESS }
        override fun setAudioAttributes(attributes: AudioAttributes): Int { this.attributes = attributes; return SUCCESS }
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener): Int { this.listener = listener; return SUCCESS }
        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String): Int {
            requests += text.toString()
            rates += appliedRate
            ids += utteranceId
            return response
        }
        override fun playSilentUtterance(durationInMs: Long, queueMode: Int, utteranceId: String): Int {
            silences += durationInMs; ids += utteranceId; return response
        }
        override fun stop(): Int { stops++; return SUCCESS }
        override fun synthesizeToFile(text: CharSequence, params: Bundle?, file: java.io.File, utteranceId: String): Int {
            requests += text.toString(); ids += utteranceId; return response
        }
    }

    private fun fixture(
        config: BetterFrenchTts.Config = BetterFrenchTts.Config(audioFocus = BetterFrenchTts.AudioFocusMode.NONE, playbackMode = BetterFrenchTts.PlaybackMode.SSML),
        prepare: (Engine) -> Unit = {},
        focusAccess: AudioFocusAccess? = null,
        block: (BetterFrenchTts, Engine) -> Unit
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var speech: BetterFrenchTts
        lateinit var engine: Engine
        instrumentation.runOnMainSync {
            lateinit var initialized: TextToSpeech.OnInitListener
            speech = BetterFrenchTts(instrumentation.targetContext,
                config, focusAccess) { context, listener ->
                initialized = listener
                Engine(context).also { engine = it; prepare(it) }
            }
            initialized.onInit(TextToSpeech.SUCCESS)
        }
        instrumentation.runOnMainSync { /* Drain the posted initialization. */ }
        try { block(speech, engine) } finally { instrumentation.runOnMainSync { speech.shutdown() } }
    }

    private fun nativeConfig() = BetterFrenchTts.Config(audioFocus = BetterFrenchTts.AudioFocusMode.NONE)
    private fun drain() { InstrumentationRegistry.getInstrumentation().runOnMainSync {} }

    @Test fun nativeSpeechUsesPlainTextAndDictionaryAliases() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        speech.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
        assertEquals(SpeechResult.Success, speech.speak("Huawei"))
        assertEquals(listOf("Oua-ouei"), engine.requests)
        assertTrue(engine.requests.none { it.contains("<speak>") })
    }

    @Test fun nativeProsodyWaitsForPreviousSegmentAndUsesRealSilence() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        speech.speak { slow { text("lent") }; pause(250); fast { text("vite") } }
        assertEquals(listOf("lent"), engine.requests)
        assertEquals(0.75f, engine.rates.single())
        engine.listener.onDone(engine.ids.last()); drain()
        assertEquals(listOf(250L), engine.silences)
        engine.listener.onDone(engine.ids.last()); drain()
        assertEquals(listOf("lent", "vite"), engine.requests)
        assertEquals(listOf(0.75f, 1.25f), engine.rates)
    }

    @Test fun flushCompletesInterruptedNativeCoroutine() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        runBlocking {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { speech.speakAndAwait("premier") }
            speech.speak("second")
            assertTrue(withTimeout(3000) { pending.await() } is SpeechResult.Error)
        }
        assertEquals(listOf("premier", "second"), engine.requests)
    }

    @Test fun engineOnStopCompletesAwaitWithoutDoneOrError() = fixture { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        runBlocking {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { speech.speakAndAwait("premier") }
            engine.listener.onStop(engine.ids.last(), true)
            assertTrue(withTimeout(3000) { pending.await() } is SpeechResult.Error)
        }
    }

    @Test fun strictOfflineInitializationRejectsMissingOrNetworkOnlyVoices() {
        for (voices in listOf(emptySet(), setOf(Voice("online", Locale.FRANCE, 500, 100, true, emptySet())))) {
            fixture(nativeConfig(), { it.offeredVoices = voices }) { speech, _ ->
                assertFalse(speech.isInitialized)
                assertEquals(SpeechResult.NotReady, speech.speak("bonjour"))
            }
        }
    }

    @Test fun regionalVoiceWinsOverHigherQualityDifferentRegion() = fixture(
        nativeConfig().copy(locale = Locale.CANADA_FRENCH), {
            it.offeredVoices = setOf(Voice("fr-high", Locale.FRANCE, 500, 100, false, emptySet()), Voice("ca-local", Locale.CANADA_FRENCH, 300, 100, false, emptySet()))
        }
    ) { speech, _ -> assertEquals("ca-local", speech.currentVoice?.name) }

    @Test fun uninstalledOfflineVoicesAreNotSelected() = fixture(nativeConfig(), {
        it.offeredVoices = setOf(Voice("missing", Locale.FRANCE, 500, 100, false, setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))
    }) { speech, _ -> assertFalse(speech.isInitialized) }

    @Test fun rejectedManualVoiceDoesNotChangeReportedVoice() = fixture(nativeConfig()) { speech, engine ->
        val before = speech.currentVoice
        engine.voiceResult = TextToSpeech.ERROR
        assertTrue(speech.trySetVoice(Voice("other", Locale.FRANCE, 300, 100, false, emptySet())) is SpeechResult.Error)
        assertEquals(before, speech.currentVoice)
    }

    @Test fun pausedQueueIgnoresOldCompletionAfterResume() = fixture(nativeConfig()) { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        speech.enqueue("un").enqueue("deux").playQueue()
        val old = engine.ids.last()
        speech.pauseQueue(); speech.resumeQueue()
        engine.listener.onDone(old); drain()
        assertEquals(0, speech.currentQueuePosition)
        assertEquals(listOf("un", "un"), engine.requests)
    }

    @Test fun failedNativeQueueDoesNotRemainPlaying() = fixture(nativeConfig()) { speech, _ ->
        assertTrue(speech.enqueue("test").playQueue() is SpeechResult.Error)
        assertFalse(speech.isQueuePlaying)
    }

    @Test fun synchronousRejectionIsReturnedForEverySpeechApi() = fixture { speech, engine ->
        assertTrue(speech.speak("bonjour") is SpeechResult.Error)
        assertTrue(speech.speak { text("bonjour".repeat(1000)) } is SpeechResult.Error)
        assertTrue(speech.speakWithPreset(SpeechPreset.NEUTRAL) { text("bonjour".repeat(1000)) } is SpeechResult.Error)
        assertEquals(3, engine.requests.size) // No later chunks submitted after a failure.
    }

    @Test fun immediateEngineFailureDoesNotLeaveCoroutineSuspended() = fixture { speech, _ ->
        runBlocking {
            withTimeout(3000) {
                assertTrue(speech.speakAndAwait("bonjour") is SpeechResult.Error)
                assertTrue(speech.speakAndAwait { text("bonjour") } is SpeechResult.Error)
            }
        }
    }

    @Test fun shutdownCompletesPendingAwaitAndRejectsFurtherSpeech() = fixture { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        runBlocking {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { speech.speakAndAwait("bonjour") }
            speech.shutdown()
            assertTrue(withTimeout(3000) { pending.await() } is SpeechResult.Error)
        }
        assertFalse(speech.isInitialized)
        assertEquals(SpeechResult.NotReady, speech.speak("après"))
    }

    @Test fun longEscapedTextAndNestedDslRespectEngineLimit() = fixture { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        assertEquals(SpeechResult.Success, speech.speak("&😀".repeat(3000)))
        assertEquals(SpeechResult.Success, speech.speak { paragraph { slow { text("&😀".repeat(3000)) } } })
        assertTrue(engine.requests.size > 2)
        assertTrue(engine.requests.all { it.length <= 4000 })
    }

    @Test fun oversizedAtomicAliasReturnsErrorInsteadOfFalseSuccess() = fixture { speech, engine ->
        engine.response = TextToSpeech.SUCCESS
        assertTrue(speech.speak { sub("mot", "a".repeat(5000)) } is SpeechResult.Error)
        assertTrue(engine.requests.isEmpty())
    }
}
