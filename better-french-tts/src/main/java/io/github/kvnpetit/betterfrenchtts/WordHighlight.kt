package io.github.kvnpetit.betterfrenchtts

/**
 * Represents the word (or text range) currently being spoken by the TTS engine.
 *
 * Positions refer to the **original text** passed to [BetterFrenchTts.speak] or
 * [BetterFrenchTts.speakAndAwait]. For DSL-based calls, positions refer to the
 * generated SSML and may not map directly to any single source string.
 *
 * ## Usage with Compose
 * ```kotlin
 * var highlight by remember { mutableStateOf<WordHighlight?>(null) }
 *
 * tts.onWordHighlight { highlight = it }
 *
 * val annotated = buildAnnotatedString {
 *     val h = highlight
 *     if (h != null && h.start in text.indices && h.end <= text.length) {
 *         append(text.substring(0, h.start))
 *         withStyle(SpanStyle(background = Color.Yellow)) {
 *             append(text.substring(h.start, h.end))
 *         }
 *         append(text.substring(h.end))
 *     } else {
 *         append(text)
 *     }
 * }
 * ```
 *
 * @property utteranceId The unique identifier of the utterance being spoken.
 * @property start Start index (inclusive) of the currently spoken range in the original text.
 * @property end End index (exclusive) of the currently spoken range in the original text.
 */
data class WordHighlight(
    val utteranceId: String,
    val start: Int,
    val end: Int,
)
