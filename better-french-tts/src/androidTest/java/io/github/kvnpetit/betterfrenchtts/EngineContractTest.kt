package io.github.kvnpetit.betterfrenchtts

import android.content.Context
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
    private class Engine(context: Context) : TextToSpeech(context, null) {
        val requests = mutableListOf<String>()
        var response = ERROR
        override fun setLanguage(locale: Locale): Int = LANG_AVAILABLE
        override fun getVoices(): Set<Voice> = emptySet()
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener): Int = SUCCESS
        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String): Int {
            requests += text.toString()
            return response
        }
        override fun stop(): Int = SUCCESS
    }

    private fun fixture(block: (BetterFrenchTts, Engine) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var speech: BetterFrenchTts
        lateinit var engine: Engine
        instrumentation.runOnMainSync {
            lateinit var initialized: TextToSpeech.OnInitListener
            speech = BetterFrenchTts(instrumentation.targetContext,
                BetterFrenchTts.Config(audioFocus = BetterFrenchTts.AudioFocusMode.NONE)) { context, listener ->
                initialized = listener
                Engine(context).also { engine = it }
            }
            initialized.onInit(TextToSpeech.SUCCESS)
        }
        try { block(speech, engine) } finally { instrumentation.runOnMainSync { speech.shutdown() } }
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
