package com.github.kvnpetit.betterfrenchtts

import com.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder

/**
 * A single item in the speech queue.
 *
 * Items are played sequentially by [BetterFrenchTts.playQueue] and can be
 * either plain text with a preset or a DSL-built speech block.
 *
 * @see BetterFrenchTts.enqueue
 * @see BetterFrenchTts.playQueue
 */
internal sealed class QueueItem {
    /**
     * A plain text item with an associated prosody preset.
     *
     * @property text The French text to speak.
     * @property preset The prosody preset applied to this item.
     */
    data class Text(val text: String, val preset: SpeechPreset) : QueueItem()

    /**
     * A DSL-built speech item.
     *
     * @property block The DSL builder block that generates the SSML content.
     */
    class Dsl internal constructor(internal val block: SpeechBuilder.() -> Unit) : QueueItem()
}
