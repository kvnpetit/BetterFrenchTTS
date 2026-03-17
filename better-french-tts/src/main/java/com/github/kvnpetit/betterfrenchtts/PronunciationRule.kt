package com.github.kvnpetit.betterfrenchtts

/**
 * Type-safe pronunciation rule for the [BetterFrenchTts] dictionary.
 *
 * Each rule maps a [word] to either a simple text alias or an exact IPA transcription.
 * A single word can only have **one** active rule — adding a new rule for the same word
 * replaces the previous one, preventing silent conflicts.
 *
 * ## Usage
 * ```kotlin
 * // Simple alias — the TTS reads "Oua-ouei" instead of "Huawei"
 * tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
 *
 * // IPA — exact phonetic control
 * tts.addPronunciation(PronunciationRule.Ipa("Huawei", "wa.wɛj"))
 * ```
 *
 * @property word The word to match in the input text (case-insensitive).
 * @see BetterFrenchTts.addPronunciation
 */
sealed interface PronunciationRule {
    val word: String

    /**
     * Simple text substitution. Generates an SSML `<sub alias="...">` tag.
     *
     * The TTS engine reads [readAs] instead of [word].
     *
     * @property word The word to match (case-insensitive).
     * @property readAs The replacement text the TTS engine speaks.
     */
    data class Alias(override val word: String, val readAs: String) : PronunciationRule

    /**
     * IPA phonetic transcription. Generates an SSML `<phoneme alphabet="ipa" ph="...">` tag.
     *
     * Provides exact phonetic control using the International Phonetic Alphabet.
     *
     * @property word The word to match (case-insensitive).
     * @property ipa The IPA transcription (e.g. `"wa.wɛj"`).
     */
    data class Ipa(override val word: String, val ipa: String) : PronunciationRule
}
