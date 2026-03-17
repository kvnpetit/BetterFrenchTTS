package com.github.kvnpetit.betterfrenchtts

/** Outcome of a speak or synthesize operation. */
sealed class SpeechResult {
    data object Success : SpeechResult()
    data class Error(val reason: String) : SpeechResult()
    data object NotReady : SpeechResult()
}
