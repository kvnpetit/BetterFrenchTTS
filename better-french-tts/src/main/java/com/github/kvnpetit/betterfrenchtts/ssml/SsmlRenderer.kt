package com.github.kvnpetit.betterfrenchtts.ssml

/** Renders an [SsmlNode] tree into an SSML XML string. */
object SsmlRenderer {

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
