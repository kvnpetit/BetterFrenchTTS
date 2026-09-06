package io.github.kvnpetit.betterfrenchtts

/** Complete preparation preview, including pronunciation rules, without engine calls. */
data class SpeechPreview(
    val originalText: String,
    val normalizedText: String,
    val nativeSteps: List<NativeSpeechStep> = emptyList(),
    val ssml: String? = null,
    val result: SpeechResult = SpeechResult.Success,
)
