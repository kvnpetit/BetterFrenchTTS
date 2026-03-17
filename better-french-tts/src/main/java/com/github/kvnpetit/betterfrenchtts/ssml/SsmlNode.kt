package com.github.kvnpetit.betterfrenchtts.ssml

/** SSML tree node. Built by [com.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder], rendered by [SsmlRenderer]. */
sealed class SsmlNode {
    data class Text(val content: String) : SsmlNode()
    data class Break(val timeMs: Int) : SsmlNode()
    data class Prosody(
        val rate: String? = null,
        val pitch: String? = null,
        val volume: String? = null,
        val children: List<SsmlNode> = emptyList()
    ) : SsmlNode()

    data class Emphasis(
        val level: String = "moderate",
        val children: List<SsmlNode> = emptyList()
    ) : SsmlNode()

    data class SayAs(
        val interpretAs: String,
        val content: String,
        val format: String? = null
    ) : SsmlNode()

    data class Sentence(val children: List<SsmlNode> = emptyList()) : SsmlNode()
    data class Paragraph(val children: List<SsmlNode> = emptyList()) : SsmlNode()
}
