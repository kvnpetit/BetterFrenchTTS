package io.github.kvnpetit.betterfrenchtts

/**
 * Represents the word (or text range) currently being spoken by the TTS engine.
 *
 * Wrapper offsets are adjusted for [BetterFrenchTts.speak] and [BetterFrenchTts.speakAndAwait], but
 * normalization, escaping, substitutions and chunking can prevent an exact mapping to the original
 * text. For DSL-based calls, positions refer to generated SSML. Validate ranges against the
 * displayed text.
 *
 * ## Usage with Compose
 *
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
 * @property start Adjusted start index (inclusive), or -1 when speech finishes.
 * @property end Adjusted end index (exclusive), or -1 when speech finishes.
 */
data class WordHighlight(val utteranceId: String, val start: Int, val end: Int)
