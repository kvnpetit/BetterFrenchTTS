package com.github.kvnpetit.betterfrenchtts

/**
 * Sealed result type representing the outcome of a speak or synthesize operation.
 *
 * ```kotlin
 * when (val result = tts.speak("Bonjour")) {
 *     is SpeechResult.Success  -> Log.d("TTS", "Speech dispatched")
 *     is SpeechResult.Error    -> Log.e("TTS", "Failed: ${result.reason}")
 *     SpeechResult.NotReady    -> Log.w("TTS", "Engine not initialized yet")
 * }
 * ```
 */
sealed class SpeechResult {
    /** The speech or synthesis request was dispatched successfully. */
    data object Success : SpeechResult()

    /**
     * The request failed.
     *
     * @property reason Human-readable description of the failure.
     */
    data class Error(val reason: String) : SpeechResult()

    /** The TTS engine is not yet initialized. Wait for [BetterFrenchTts.Config.onReady]. */
    data object NotReady : SpeechResult()
}
