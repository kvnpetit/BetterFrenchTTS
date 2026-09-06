package io.github.kvnpetit.betterfrenchtts.ssml

/**
 * Sealed hierarchy representing nodes in an SSML tree.
 *
 * Nodes are constructed by [io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder] and rendered
 * to an XML string by [SsmlRenderer].
 *
 * This is an internal representation — library consumers interact with the DSL and never
 * need to create [SsmlNode] instances directly.
 *
 * @see SsmlRenderer.render
 */
sealed class SsmlNode {

    /** Raw text content. Rendered as XML-escaped text. */
    data class Text(val content: String) : SsmlNode()

    /**
     * Silence pause. Rendered as `<break time="[timeMs]ms"/>`.
     *
     * @property timeMs Duration in milliseconds.
     */
    data class Break(val timeMs: Int) : SsmlNode()

    /**
     * Prosody modifier. Rendered as `<prosody rate="..." pitch="..." volume="...">`.
     *
     * @property rate SSML rate value, or `null` to omit.
     * @property pitch SSML pitch value, or `null` to omit.
     * @property volume SSML volume value, or `null` to omit.
     * @property children Child nodes wrapped by this prosody element.
     */
    data class Prosody(
        val rate: String? = null,
        val pitch: String? = null,
        val volume: String? = null,
        val children: List<SsmlNode> = emptyList()
    ) : SsmlNode()

    /**
     * Emphasis modifier. Rendered as `<emphasis level="...">`.
     *
     * @property level One of `"strong"`, `"moderate"`, or `"reduced"`.
     * @property children Child nodes wrapped by this emphasis element.
     */
    data class Emphasis(
        val level: String = "moderate",
        val children: List<SsmlNode> = emptyList()
    ) : SsmlNode()

    /**
     * Interpretation directive. Rendered as `<say-as interpret-as="...">`.
     *
     * @property interpretAs The SSML interpretation type (e.g. `"cardinal"`, `"ordinal"`, `"date"`, `"telephone"`, `"characters"`).
     * @property content The text to interpret.
     * @property format Optional format attribute (used with `"date"`, e.g. `"dmy"`).
     */
    data class SayAs(
        val interpretAs: String,
        val content: String,
        val format: String? = null
    ) : SsmlNode()

    /**
     * Phonetic pronunciation. Rendered as `<phoneme alphabet="ipa" ph="...">...</phoneme>`.
     *
     * Provides exact phonetic control using the International Phonetic Alphabet (IPA).
     *
     * @property content The original text displayed to the user.
     * @property ph The IPA transcription that the TTS engine speaks.
     */
    data class Phoneme(val content: String, val ph: String) : SsmlNode()

    /**
     * Substitution alias. Rendered as `<sub alias="...">...</sub>`.
     *
     * The TTS engine reads the [alias] text instead of the [content].
     *
     * @property content The original text displayed to the user.
     * @property alias The replacement text that the TTS engine actually speaks.
     */
    data class Sub(val content: String, val alias: String) : SsmlNode()

    /**
     * Sentence wrapper. Rendered as `<s>...</s>`.
     *
     * @property children Child nodes forming the sentence.
     */
    data class Sentence(val children: List<SsmlNode> = emptyList()) : SsmlNode()

    /**
     * Paragraph wrapper. Rendered as `<p>...</p>`.
     *
     * @property children Child nodes forming the paragraph.
     */
    data class Paragraph(val children: List<SsmlNode> = emptyList()) : SsmlNode()
}
