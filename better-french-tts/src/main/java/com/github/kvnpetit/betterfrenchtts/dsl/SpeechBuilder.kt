package com.github.kvnpetit.betterfrenchtts.dsl

import com.github.kvnpetit.betterfrenchtts.SpeechPreset
import com.github.kvnpetit.betterfrenchtts.spelling.FrenchCharMap
import com.github.kvnpetit.betterfrenchtts.ssml.SsmlNode

@DslMarker
annotation class SpeechDsl

/**
 * DSL builder for constructing structured speech with SSML generation.
 *
 * Example:
 * ```
 * tts.speak {
 *     text("Bonjour.")
 *     pause(400)
 *     slow { text("Ceci est important.") }
 * }
 * ```
 */
@SpeechDsl
class SpeechBuilder {
    internal val nodes = mutableListOf<SsmlNode>()

    /** Adds plain text. */
    fun text(content: String) {
        nodes += SsmlNode.Text(content)
    }

    /** Inserts a pause of [timeMs] milliseconds. */
    fun pause(timeMs: Int) {
        nodes += SsmlNode.Break(timeMs)
    }

    /** Wraps content in a `<prosody>` tag with optional rate, pitch, and volume. */
    fun prosody(
        rate: String? = null,
        pitch: String? = null,
        volume: String? = null,
        block: SpeechBuilder.() -> Unit
    ) {
        val inner = SpeechBuilder().apply(block).nodes
        nodes += SsmlNode.Prosody(rate = rate, pitch = pitch, volume = volume, children = inner)
    }

    /** Applies a [SpeechPreset]'s prosody to the content. */
    fun withPreset(preset: SpeechPreset, block: SpeechBuilder.() -> Unit) {
        prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume, block = block)
    }

    // -- Rate shortcuts --

    fun slow(block: SpeechBuilder.() -> Unit) = prosody(rate = "slow", block = block)
    fun fast(block: SpeechBuilder.() -> Unit) = prosody(rate = "fast", block = block)
    fun xSlow(block: SpeechBuilder.() -> Unit) = prosody(rate = "x-slow", block = block)
    fun xFast(block: SpeechBuilder.() -> Unit) = prosody(rate = "x-fast", block = block)

    /** Sets rate as a percentage of normal speed (e.g. 75 = 75%). */
    fun rate(percent: Int, block: SpeechBuilder.() -> Unit) = prosody(rate = "$percent%", block = block)

    // -- Volume shortcuts --

    fun soft(block: SpeechBuilder.() -> Unit) = prosody(volume = "soft", block = block)
    fun loud(block: SpeechBuilder.() -> Unit) = prosody(volume = "loud", block = block)
    fun xSoft(block: SpeechBuilder.() -> Unit) = prosody(volume = "x-soft", block = block)
    fun xLoud(block: SpeechBuilder.() -> Unit) = prosody(volume = "x-loud", block = block)

    // -- Pitch shortcuts --

    fun highPitch(block: SpeechBuilder.() -> Unit) = prosody(pitch = "+3st", block = block)
    fun lowPitch(block: SpeechBuilder.() -> Unit) = prosody(pitch = "-3st", block = block)

    /** Sets pitch in semitones relative to default (e.g. 5 = +5st, -2 = -2st). */
    fun pitch(semitones: Int, block: SpeechBuilder.() -> Unit) {
        val sign = if (semitones >= 0) "+" else ""
        prosody(pitch = "${sign}${semitones}st", block = block)
    }

    // -- Emphasis --

    fun emphasis(level: String = "moderate", block: SpeechBuilder.() -> Unit) {
        val inner = SpeechBuilder().apply(block).nodes
        nodes += SsmlNode.Emphasis(level = level, children = inner)
    }

    fun strong(block: SpeechBuilder.() -> Unit) = emphasis(level = "strong", block = block)
    fun reduced(block: SpeechBuilder.() -> Unit) = emphasis(level = "reduced", block = block)

    // -- Say-as interpretations --

    /** Generic say-as with a custom [interpretAs] type. */
    fun sayAs(interpretAs: String, content: String) {
        nodes += SsmlNode.SayAs(interpretAs = interpretAs, content = content)
    }

    /**
     * Spells out [content] character by character with French pronunciation.
     * Accented chars, symbols, and special characters are all handled via [FrenchCharMap].
     *
     * @param pauseMs pause between each character in milliseconds.
     */
    fun spellOut(content: String, pauseMs: Int = 150) {
        content.forEachIndexed { index, char ->
            when {
                char == ' ' -> nodes += SsmlNode.Break(pauseMs * 2)
                char == '\n' || char == '\t' -> nodes += SsmlNode.Break(pauseMs * 3)
                char == '\u00A0' || char == '\u202F' || char == '\u200B' -> {
                    nodes += SsmlNode.Break(pauseMs * 2)
                }
                else -> {
                    val spoken = FrenchCharMap.resolve(char)
                    if (spoken != null) {
                        nodes += SsmlNode.Text(spoken)
                    } else {
                        nodes += SsmlNode.SayAs(interpretAs = "characters", content = char.toString())
                    }
                    if (index < content.lastIndex) {
                        val next = content[index + 1]
                        if (next != ' ' && next != '\n' && next != '\t') {
                            nodes += SsmlNode.Break(pauseMs)
                        }
                    }
                }
            }
        }
    }

    fun date(content: String, format: String = "dmy") {
        nodes += SsmlNode.SayAs(interpretAs = "date", content = content, format = format)
    }

    /** Reads [content] as a cardinal number (e.g. "1500" -> "mille cinq cents"). */
    fun number(content: String) = sayAs(interpretAs = "cardinal", content = content)

    /** Reads [content] as an ordinal (e.g. "3" -> "troisieme"). */
    fun ordinal(content: String) = sayAs(interpretAs = "ordinal", content = content)

    /** Reads [content] as a telephone number. */
    fun telephone(content: String) = sayAs(interpretAs = "telephone", content = content)

    // -- Structure --

    fun sentence(block: SpeechBuilder.() -> Unit) {
        val inner = SpeechBuilder().apply(block).nodes
        nodes += SsmlNode.Sentence(children = inner)
    }

    fun paragraph(block: SpeechBuilder.() -> Unit) {
        val inner = SpeechBuilder().apply(block).nodes
        nodes += SsmlNode.Paragraph(children = inner)
    }
}
