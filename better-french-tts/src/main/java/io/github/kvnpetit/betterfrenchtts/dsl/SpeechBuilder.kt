package io.github.kvnpetit.betterfrenchtts.dsl

import io.github.kvnpetit.betterfrenchtts.SpeechPreset
import io.github.kvnpetit.betterfrenchtts.spelling.FrenchCharMap
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode

/** DSL marker that prevents accidental nesting of [SpeechBuilder] receivers. */
@DslMarker
annotation class SpeechDsl

/**
 * Kotlin DSL builder for constructing structured speech with automatic SSML generation.
 *
 * This builder provides a type-safe, readable way to compose complex speech sequences
 * including pauses, prosody changes, emphasis, spell-out, and structured say-as interpretations.
 *
 * ## Basic usage
 * ```kotlin
 * tts.speak {
 *     text("Bonjour.")
 *     pause(400)
 *     slow { text("Ceci est important.") }
 * }
 * ```
 *
 * ## Rich example
 * ```kotlin
 * tts.speak {
 *     paragraph {
 *         sentence { text("Bienvenue sur notre application.") }
 *         sentence {
 *             text("Votre code est ")
 *             spellOut("AB12")
 *             text(".")
 *         }
 *     }
 *     pause(600)
 *     withPreset(SpeechPreset.CALM) {
 *         text("Merci et à bientôt.")
 *     }
 * }
 * ```
 *
 * @see io.github.kvnpetit.betterfrenchtts.BetterFrenchTts.speak
 * @see SsmlNode
 */
@SpeechDsl
class SpeechBuilder {
    internal val nodes = mutableListOf<SsmlNode>()

    /**
     * Adds plain text to the speech output.
     *
     * Special XML characters (`<`, `>`, `&`, etc.) are escaped automatically.
     *
     * @param content The text to speak.
     */
    fun text(content: String) {
        nodes += SsmlNode.Text(content)
    }

    /**
     * Inserts a silence pause.
     *
     * Generates an SSML `<break time="...ms"/>` element.
     *
     * @param timeMs Duration of the pause in milliseconds.
     */
    fun pause(timeMs: Int) {
        nodes += SsmlNode.Break(timeMs)
    }

    /**
     * Wraps content in an SSML `<prosody>` tag with optional rate, pitch, and volume.
     *
     * @param rate Speech rate: `"x-slow"`, `"slow"`, `"medium"`, `"fast"`, `"x-fast"`, or a percentage.
     * @param pitch Pitch shift in semitones (e.g. `"+2st"`, `"-1st"`).
     * @param volume Volume level: `"x-soft"`, `"soft"`, `"medium"`, `"loud"`, `"x-loud"`.
     * @param block DSL block for the content affected by this prosody.
     */
    fun prosody(
        rate: String? = null,
        pitch: String? = null,
        volume: String? = null,
        block: SpeechBuilder.() -> Unit
    ) {
        nodes += SsmlNode.Prosody(rate = rate, pitch = pitch, volume = volume, children = buildChildren(block))
    }

    /**
     * Applies a [SpeechPreset]'s prosody (rate, pitch, volume) to the enclosed content.
     *
     * @param preset The preset whose values are used for the `<prosody>` tag.
     * @param block DSL block for the content affected by this preset.
     */
    fun withPreset(preset: SpeechPreset, block: SpeechBuilder.() -> Unit) {
        prosody(rate = preset.rate, pitch = preset.pitch, volume = preset.volume, block = block)
    }

    // -- Rate shortcuts --

    /** Wraps content with `rate="slow"`. */
    fun slow(block: SpeechBuilder.() -> Unit) = prosody(rate = "slow", block = block)
    /** Wraps content with `rate="fast"`. */
    fun fast(block: SpeechBuilder.() -> Unit) = prosody(rate = "fast", block = block)
    /** Wraps content with `rate="x-slow"`. */
    fun xSlow(block: SpeechBuilder.() -> Unit) = prosody(rate = "x-slow", block = block)
    /** Wraps content with `rate="x-fast"`. */
    fun xFast(block: SpeechBuilder.() -> Unit) = prosody(rate = "x-fast", block = block)

    /**
     * Sets rate as a percentage of normal speed.
     *
     * @param percent Speed percentage (e.g. `75` for 75% of normal speed, `120` for 120%).
     * @param block DSL block for the affected content.
     */
    fun rate(percent: Int, block: SpeechBuilder.() -> Unit) = prosody(rate = "$percent%", block = block)

    // -- Volume shortcuts --

    /** Wraps content with `volume="soft"`. */
    fun soft(block: SpeechBuilder.() -> Unit) = prosody(volume = "soft", block = block)
    /** Wraps content with `volume="loud"`. */
    fun loud(block: SpeechBuilder.() -> Unit) = prosody(volume = "loud", block = block)
    /** Wraps content with `volume="x-soft"`. */
    fun xSoft(block: SpeechBuilder.() -> Unit) = prosody(volume = "x-soft", block = block)
    /** Wraps content with `volume="x-loud"`. */
    fun xLoud(block: SpeechBuilder.() -> Unit) = prosody(volume = "x-loud", block = block)

    // -- Pitch shortcuts --

    /** Wraps content with `pitch="+3st"` (higher). */
    fun highPitch(block: SpeechBuilder.() -> Unit) = prosody(pitch = "+3st", block = block)
    /** Wraps content with `pitch="-3st"` (lower). */
    fun lowPitch(block: SpeechBuilder.() -> Unit) = prosody(pitch = "-3st", block = block)

    /**
     * Sets pitch in semitones relative to default.
     *
     * @param semitones Offset in semitones (e.g. `5` produces `+5st`, `-2` produces `-2st`).
     * @param block DSL block for the affected content.
     */
    fun pitch(semitones: Int, block: SpeechBuilder.() -> Unit) {
        val sign = if (semitones >= 0) "+" else ""
        prosody(pitch = "${sign}${semitones}st", block = block)
    }

    // -- Emphasis --

    /**
     * Wraps content in an SSML `<emphasis>` tag.
     *
     * @param level Emphasis level: `"strong"`, `"moderate"` (default), or `"reduced"`.
     * @param block DSL block for the emphasized content.
     */
    fun emphasis(level: String = "moderate", block: SpeechBuilder.() -> Unit) {
        nodes += SsmlNode.Emphasis(level = level, children = buildChildren(block))
    }

    /** Shortcut for `emphasis(level = "strong")`. */
    fun strong(block: SpeechBuilder.() -> Unit) = emphasis(level = "strong", block = block)
    /** Shortcut for `emphasis(level = "reduced")`. */
    fun reduced(block: SpeechBuilder.() -> Unit) = emphasis(level = "reduced", block = block)

    // -- Phoneme --

    /**
     * Provides exact phonetic pronunciation using the International Phonetic Alphabet (IPA).
     *
     * Generates an SSML `<phoneme alphabet="ipa" ph="...">...</phoneme>` element.
     *
     * ```kotlin
     * tts.speak {
     *     text("Le mot ")
     *     phoneme("Huawei", "wa.wɛj")
     *     text(" est chinois.")
     * }
     * ```
     *
     * @param content The original text (displayed but overridden by phonetics).
     * @param ipa The IPA transcription the TTS engine uses for pronunciation.
     */
    fun phoneme(content: String, ipa: String) {
        nodes += SsmlNode.Phoneme(content = content, ph = ipa)
    }

    // -- Substitution --

    /**
     * Substitutes [content] with [alias] for pronunciation.
     *
     * The TTS engine speaks the [alias] text instead of [content].
     * Generates an SSML `<sub alias="...">...</sub>` element.
     *
     * ```kotlin
     * tts.speak {
     *     text("J'utilise ")
     *     sub("Huawei", "Oua-ouei")
     *     text(" depuis 2 ans.")
     * }
     * ```
     *
     * @param content The original text (displayed but not spoken).
     * @param alias The replacement pronunciation.
     */
    fun sub(content: String, alias: String) {
        nodes += SsmlNode.Sub(content = content, alias = alias)
    }

    // -- Say-as interpretations --

    /**
     * Generates an SSML `<say-as>` element with a custom interpretation type.
     *
     * @param interpretAs The SSML interpret-as value (e.g. `"cardinal"`, `"ordinal"`, `"date"`, `"telephone"`, `"characters"`).
     * @param content The text to interpret.
     */
    fun sayAs(interpretAs: String, content: String) {
        nodes += SsmlNode.SayAs(interpretAs = interpretAs, content = content)
    }

    /**
     * Spells out [content] character by character with French pronunciation.
     *
     * Each character is resolved via [FrenchCharMap] to its spoken French name
     * (e.g. `'é'` → "é accent aigu", `'@'` → "arobase"). Plain letters (a-z) and
     * digits (0-9) are handled natively by the TTS engine via `<say-as interpret-as="characters">`.
     *
     * Spaces insert a double pause, newlines/tabs a triple pause.
     *
     * @param content The text to spell out (e.g. `"AB-12"`, `"mot@test.fr"`).
     * @param pauseMs Pause between each character in milliseconds (default: 150).
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

    /**
     * Reads [content] as a date.
     *
     * @param content The date string to interpret (e.g. `"25/12/2025"`).
     * @param format SSML date format: `"dmy"` (default), `"mdy"`, `"ymd"`, `"md"`, `"dm"`, etc.
     */
    fun date(content: String, format: String = "dmy") {
        nodes += SsmlNode.SayAs(interpretAs = "date", content = content, format = format)
    }

    /**
     * Reads [content] as a cardinal number (e.g. `"1500"` → "mille cinq cents").
     *
     * @param content The numeric string to interpret.
     */
    fun number(content: String) = sayAs(interpretAs = "cardinal", content = content)

    /**
     * Reads [content] as an ordinal number (e.g. `"3"` → "troisième").
     *
     * @param content The numeric string to interpret.
     */
    fun ordinal(content: String) = sayAs(interpretAs = "ordinal", content = content)

    /**
     * Reads [content] as a telephone number with appropriate digit grouping.
     *
     * @param content The phone number string (e.g. `"01 23 45 67 89"`).
     */
    fun telephone(content: String) = sayAs(interpretAs = "telephone", content = content)

    // -- Structure --

    /**
     * Wraps content in an SSML `<s>` (sentence) tag.
     *
     * The TTS engine adds natural sentence-level pauses and intonation.
     *
     * @param block DSL block for the sentence content.
     */
    fun sentence(block: SpeechBuilder.() -> Unit) {
        nodes += SsmlNode.Sentence(children = buildChildren(block))
    }

    /**
     * Wraps content in an SSML `<p>` (paragraph) tag.
     *
     * The TTS engine adds natural paragraph-level pauses.
     *
     * @param block DSL block for the paragraph content.
     */
    fun paragraph(block: SpeechBuilder.() -> Unit) {
        nodes += SsmlNode.Paragraph(children = buildChildren(block))
    }

    private fun buildChildren(block: SpeechBuilder.() -> Unit): List<SsmlNode> {
        return SpeechBuilder().apply(block).nodes
    }
}
