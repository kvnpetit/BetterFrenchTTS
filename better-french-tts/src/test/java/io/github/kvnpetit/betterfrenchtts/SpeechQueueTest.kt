package io.github.kvnpetit.betterfrenchtts

import org.junit.Assert.*
import org.junit.Test

class SpeechQueueTest {
    private class Fixture {
        val posted = ArrayDeque<() -> Unit>()
        val completions = mutableListOf<(SpeechResult) -> Unit>()
        val dispatched = mutableListOf<QueueItem>()
        val progress = mutableListOf<QueueProgress>()
        var ready = true
        var interruptions = 0
        var finished = 0
        var result: SpeechResult = SpeechResult.Success
        val queue =
            SpeechQueue(
                isReady = { ready },
                interrupt = { interruptions++ },
                dispatch = { item, complete ->
                    dispatched += item
                    completions += complete
                    result
                },
                releaseFocus = {},
                post = { posted.addLast(it) },
                onProgress = { progress += it },
                onFinished = { finished++ },
            )

        fun drain() {
            while (posted.isNotEmpty()) posted.removeFirst().invoke()
        }

        fun addTwo() {
            queue.addAll(
                listOf(
                    QueueItem.Text("un", SpeechPreset.NEUTRAL),
                    QueueItem.Text("deux", SpeechPreset.NEUTRAL),
                )
            )
        }
    }

    @Test
    fun unavailableAndEmptyQueuesDoNotInterruptPlayback() {
        val fixture = Fixture()
        with(fixture) {
            ready = false
            assertEquals(SpeechResult.NotReady, queue.playQueue())
            ready = true
            assertTrue(queue.playQueue() is SpeechResult.Error)
            assertEquals(0, interruptions)
        }
    }

    @Test
    fun restartDropsPostedProgressFromPreviousGeneration() {
        with(Fixture()) {
            addTwo()
            queue.playQueue()
            queue.playQueue()
            completions.first()(SpeechResult.Success)
            drain()
            assertEquals(1, progress.size)
            assertEquals(0, queue.position)
            assertEquals(2, queue.size)
        }
    }

    @Test
    fun pauseAndResumeReplayTheSameItem() {
        with(Fixture()) {
            addTwo()
            queue.playQueue()
            queue.pauseQueue()
            assertTrue(queue.isPaused)
            completions.first()(SpeechResult.Success)
            assertEquals(SpeechResult.Success, queue.resumeQueue())
            assertEquals(dispatched.first(), dispatched.last())
            assertTrue(queue.isPlaying)
        }
    }

    @Test
    fun skipIgnoresCompletionOfTheInterruptedItem() {
        with(Fixture()) {
            addTwo()
            queue.playQueue()
            queue.skipToNext()
            completions.first()(SpeechResult.Success)
            assertEquals(1, queue.position)
            completions.last()(SpeechResult.Success)
            drain()
            assertEquals(1, finished)
            assertFalse(queue.isActive)
        }
    }

    @Test
    fun resetSuppressesAlreadyPostedFinish() {
        with(Fixture()) {
            queue.add(QueueItem.Text("un", SpeechPreset.NEUTRAL))
            queue.playQueue()
            completions.single()(SpeechResult.Success)
            queue.resetQueueState()
            drain()
            assertEquals(0, finished)
            assertEquals(0, queue.size)
        }
    }

    @Test
    fun immediateFailureClearsQueueWithoutSuccessNotification() {
        with(Fixture()) {
            addTwo()
            result = SpeechResult.Error("rejected")
            assertEquals(result, queue.playQueue())
            drain()
            assertFalse(queue.isActive)
            assertEquals(0, queue.size)
            assertEquals(0, finished)
        }
    }
}
