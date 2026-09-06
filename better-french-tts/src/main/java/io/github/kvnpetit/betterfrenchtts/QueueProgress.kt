package io.github.kvnpetit.betterfrenchtts

/**
 * Progress information emitted while a speech queue is playing.
 *
 * Emitted each time a new queue item starts playing, via [BetterFrenchTts.onQueueProgress].
 *
 * @property currentIndex Zero-based index of the item currently being spoken.
 * @property totalItems Total number of items in the queue.
 * @see BetterFrenchTts.onQueueProgress
 * @see BetterFrenchTts.playQueue
 */
data class QueueProgress(val currentIndex: Int, val totalItems: Int)
