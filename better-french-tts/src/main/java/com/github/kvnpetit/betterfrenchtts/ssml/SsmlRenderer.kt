package com.github.kvnpetit.betterfrenchtts.ssml

/**
 * Renders an [SsmlNode] tree into a complete SSML XML string.
 *
 * The output is wrapped in a `<speak>` root element as required by the Android TTS engine.
 * All text content is XML-escaped to prevent injection of unintended markup.
 *
 * This is an internal component — library consumers use [com.github.kvnpetit.betterfrenchtts.BetterFrenchTts]
 * which calls the renderer automatically.
 */
object SsmlRenderer {

    /**
     * Renders a list of [SsmlNode] into a complete SSML document.
     *
     * @param nodes The root-level SSML nodes to render.
     * @return A string like `<speak>...<prosody ...>...</prosody>...</speak>`.
     */
    fun render(nodes: List<SsmlNode>): String {
        val body = nodes.joinToString("") { renderNode(it) }
        return "<speak>$body</speak>"
    }

    private fun renderNode(node: SsmlNode): String = when (node) {
        is SsmlNode.Text -> escapeXml(node.content)

        is SsmlNode.Break -> "<break time=\"${node.timeMs}ms\"/>"

        is SsmlNode.Prosody -> {
            val attrs = buildList {
                node.rate?.let { add("rate=\"$it\"") }
                node.pitch?.let { add("pitch=\"$it\"") }
                node.volume?.let { add("volume=\"$it\"") }
            }.joinToString(" ")
            val inner = node.children.joinToString("") { renderNode(it) }
            if (attrs.isNotEmpty()) "<prosody $attrs>$inner</prosody>" else inner
        }

        is SsmlNode.Emphasis -> {
            val inner = node.children.joinToString("") { renderNode(it) }
            "<emphasis level=\"${node.level}\">$inner</emphasis>"
        }

        is SsmlNode.Phoneme -> "<phoneme alphabet=\"ipa\" ph=\"${escapeXml(node.ph)}\">${escapeXml(node.content)}</phoneme>"

        is SsmlNode.Sub -> "<sub alias=\"${escapeXml(node.alias)}\">${escapeXml(node.content)}</sub>"

        is SsmlNode.SayAs -> {
            val formatAttr = node.format?.let { " format=\"$it\"" } ?: ""
            "<say-as interpret-as=\"${node.interpretAs}\"$formatAttr>${escapeXml(node.content)}</say-as>"
        }

        is SsmlNode.Sentence -> {
            val inner = node.children.joinToString("") { renderNode(it) }
            "<s>$inner</s>"
        }

        is SsmlNode.Paragraph -> {
            val inner = node.children.joinToString("") { renderNode(it) }
            "<p>$inner</p>"
        }
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
