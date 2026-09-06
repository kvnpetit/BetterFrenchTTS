package io.github.kvnpetit.betterfrenchtts

import java.util.concurrent.atomic.AtomicLong

/** Queue state machine, independent of Android engines and rendering. */
internal class SpeechQueue(
    private val isReady: () -> Boolean,
    private val interrupt: () -> Unit,
    private val dispatch: (QueueItem, (SpeechResult) -> Unit) -> SpeechResult,
    private val releaseFocus: () -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val onProgress: (QueueProgress) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val queueItems = mutableListOf<QueueItem>()
    private val queueGeneration = AtomicLong(0)
    @Volatile private var queueIndex = -1
    @Volatile private var queueActive = false
    @Volatile private var queuePaused = false

    val isActive: Boolean
        get() = queueActive

    val isPlaying: Boolean
        get() = queueActive && !queuePaused

    val isPaused: Boolean
        get() = queueActive && queuePaused

    val size: Int
        get() = synchronized(queueItems) { queueItems.size }

    val position: Int
        get() = queueIndex

    fun add(item: QueueItem) {
        synchronized(queueItems) { queueItems += item }
    }

    fun addAll(items: List<QueueItem>) {
        synchronized(queueItems) { queueItems += items }
    }

    fun playQueue(): SpeechResult {
        if (!isReady()) return SpeechResult.NotReady
        if (synchronized(queueItems) { queueItems.isEmpty() })
            return SpeechResult.Error("Queue is empty")
        queueGeneration.incrementAndGet()
        queuePaused = true
        interrupt()
        synchronized(queueItems) {
            if (queueItems.isEmpty()) return SpeechResult.Error("Queue is empty")
            queueIndex = 0
            queueActive = true
            queuePaused = false
        }
        return dispatchCurrentQueueItem()
    }

    fun pauseQueue(): SpeechResult {
        if (!queueActive) return SpeechResult.Error("No queue is active")
        queueGeneration.incrementAndGet()
        queuePaused = true
        interrupt()
        return SpeechResult.Success
    }

    fun resumeQueue(): SpeechResult {
        if (!isReady()) return SpeechResult.NotReady
        if (!queueActive || !queuePaused) return SpeechResult.Error("Queue is not paused")
        queuePaused = false
        return dispatchCurrentQueueItem()
    }

    fun skipToNext(): SpeechResult {
        if (!isReady()) return SpeechResult.NotReady
        if (!queueActive) return SpeechResult.Error("No queue is active")
        queueGeneration.incrementAndGet()
        queuePaused = true
        interrupt()
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

        post {
            if (queueGeneration.get() == generation && queueActive)
                onProgress(QueueProgress(position, total))
        }

        val onItemDone: (SpeechResult) -> Unit = { result ->
            if (queueGeneration.get() == generation && queueActive && !queuePaused) {
                if (result == SpeechResult.Success) advanceQueue()
                else {
                    resetQueueState()
                    interrupt()
                }
            }
        }

        val result =
            try {
                dispatch(item, onItemDone)
            } catch (error: IllegalArgumentException) {
                SpeechResult.Error(error.message ?: "Invalid queued speech")
            }
        if (result != SpeechResult.Success) resetQueueState()
        return result
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
        releaseFocus()
        post { if (queueGeneration.get() == generation && !queueActive) onFinished() }
    }

    fun resetQueueState() {
        queueGeneration.incrementAndGet()
        queueActive = false
        queuePaused = false
        queueIndex = -1
        synchronized(queueItems) { queueItems.clear() }
    }
}
