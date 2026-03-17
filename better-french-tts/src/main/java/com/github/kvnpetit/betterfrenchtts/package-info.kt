/**
 * # BetterFrenchTTS
 *
 * A high-level Android library that wraps the native [android.speech.tts.TextToSpeech] engine
 * with automatic French voice selection, SSML generation, and a Kotlin DSL.
 *
 * ## Main entry point
 * - [BetterFrenchTts] — the main wrapper class. Handles initialization, voice selection, speech,
 *   file synthesis, and lifecycle management.
 *
 * ## Speech configuration
 * - [SpeechPreset] — named prosody presets (rate, pitch, volume) with 12 built-in options.
 * - [SpeechResult] — sealed result type returned by all speak/synthesize operations.
 *
 * ## Sub-packages
 * - [com.github.kvnpetit.betterfrenchtts.dsl] — Kotlin DSL builder for structured speech.
 * - [com.github.kvnpetit.betterfrenchtts.voice] — French voice selection algorithm.
 * - [com.github.kvnpetit.betterfrenchtts.ssml] — SSML node tree and XML renderer.
 * - [com.github.kvnpetit.betterfrenchtts.spelling] — French character name map for spell-out.
 *
 * ## Quick start
 * ```kotlin
 * val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
 *     defaultPreset = SpeechPreset.CALM,
 *     onReady = { engine ->
 *         engine.speak("Bonjour le monde !")
 *     }
 * ))
 *
 * // Don't forget to release resources:
 * tts.shutdown()
 * ```
 *
 * @see BetterFrenchTts
 * @see SpeechPreset
 * @see SpeechResult
 */
package com.github.kvnpetit.betterfrenchtts
