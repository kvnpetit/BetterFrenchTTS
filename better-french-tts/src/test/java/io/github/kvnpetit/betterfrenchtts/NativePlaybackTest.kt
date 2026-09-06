package io.github.kvnpetit.betterfrenchtts

import org.junit.Assert.*
import org.junit.Test

class NativePlaybackTest {
    @Test fun segmentsAndAddedJobsWaitForCompletion() {
        val texts = mutableListOf<String>()
        val callbacks = ArrayDeque<(SpeechResult) -> Unit>()
        val playback = NativePlayback({ step, done -> texts += step.text; callbacks.addLast(done); SpeechResult.Success }, { it() })
        val outcomes = mutableListOf<SpeechResult>()
        playback.enqueue(listOf(NativeSpeechStep("a"), NativeSpeechStep("b")), outcomes::add)
        playback.enqueue(listOf(NativeSpeechStep("c")), outcomes::add)
        assertEquals(listOf("a"), texts)
        callbacks.removeFirst()(SpeechResult.Success)
        assertEquals(listOf("a", "b"), texts)
        callbacks.removeFirst()(SpeechResult.Success)
        assertEquals(listOf("a", "b", "c"), texts)
        callbacks.removeFirst()(SpeechResult.Success)
        assertEquals(listOf(SpeechResult.Success, SpeechResult.Success), outcomes)
    }
    @Test fun interruptionCompletesAllJobsAndIgnoresStaleCallbacks() {
        lateinit var callback: (SpeechResult) -> Unit
        var submissions = 0
        val playback = NativePlayback({ _, done -> submissions++; callback = done; SpeechResult.Success }, { it() })
        val outcomes = mutableListOf<SpeechResult>()
        playback.enqueue(listOf(NativeSpeechStep("a"), NativeSpeechStep("b")), outcomes::add)
        playback.enqueue(listOf(NativeSpeechStep("c")), outcomes::add)
        playback.cancel()
        callback(SpeechResult.Success)
        assertEquals(1, submissions)
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is SpeechResult.Error })
    }
    @Test fun callbackCanEnqueueNextJobWithoutDoubleDispatch() {
        val callbacks = ArrayDeque<(SpeechResult) -> Unit>()
        var submissions = 0
        val playback = NativePlayback({ _, done -> submissions++; callbacks.addLast(done); SpeechResult.Success }, { it() })
        playback.enqueue(listOf(NativeSpeechStep("a"))) { playback.enqueue(listOf(NativeSpeechStep("b"))) {} }
        callbacks.removeFirst()(SpeechResult.Success)
        assertEquals(2, submissions)
    }
    @Test fun rejectionDoesNotSubmitLaterSegments() {
        var submissions = 0
        val playback = NativePlayback({ _, _ -> submissions++; SpeechResult.Error("rejected") }, { it() })
        var result: SpeechResult? = null
        assertTrue(playback.enqueue(listOf(NativeSpeechStep("a"), NativeSpeechStep("b"))) { result = it } is SpeechResult.Error)
        assertEquals(1, submissions)
        assertTrue(result is SpeechResult.Error)
    }
}
