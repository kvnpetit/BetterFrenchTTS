/**
 * Kotlin DSL for composing structured French speech.
 *
 * The [SpeechBuilder] provides a type-safe, readable API to build complex speech sequences
 * with pauses, prosody changes, emphasis, spell-out, say-as interpretations, and structural
 * elements (sentences, paragraphs).
 *
 * ```kotlin
 * tts.speak {
 *     paragraph {
 *         sentence { text("Bienvenue.") }
 *         sentence {
 *             text("Votre code est ")
 *             spellOut("AB12")
 *             text(".")
 *         }
 *     }
 *     pause(500)
 *     withPreset(SpeechPreset.CALM) {
 *         text("Merci et à bientôt.")
 *     }
 * }
 * ```
 *
 * @see SpeechBuilder
 * @see com.github.kvnpetit.betterfrenchtts.BetterFrenchTts.speak
 */
package com.github.kvnpetit.betterfrenchtts.dsl
