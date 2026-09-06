/**
 * Intelligent French text preprocessing before TTS synthesis.
 *
 * [FrenchTextPreprocessor] normalizes common French patterns that Android TTS engines
 * often mispronounce: abbreviations, ordinals, time formats, units, roman numerals,
 * currency symbols, and percentages.
 *
 * @see FrenchTextPreprocessor
 * @see io.github.kvnpetit.betterfrenchtts.BetterFrenchTts.Config.preprocessText
 */
package io.github.kvnpetit.betterfrenchtts.preprocessing
