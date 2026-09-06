package io.github.kvnpetit.betterfrenchtts.ssml

/**
 * Renders an [SsmlNode] tree into a complete SSML XML string.
 *
 * The output is wrapped in an SSML `<speak>` root element. Interpretation of this
 * markup depends on the installed TTS engine; Android does not guarantee SSML support.
 * All text content is XML-escaped to prevent injection of unintended markup.
 *
 * This is an internal component — library consumers use [io.github.kvnpetit.betterfrenchtts.BetterFrenchTts]
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

    /**
     * Splits [nodes] into groups whose rendered content fits within [maxContentLength] characters.
     *
     * Each group can then be wrapped and rendered separately. This is used internally
     * to chunk DSL-generated SSML that exceeds the TTS engine's ~4 000 character limit.
     *
     * @param nodes The SSML nodes to split.
     * @param maxContentLength Maximum rendered length of the nodes in each group
     *   (excluding any outer wrapper like `<speak>` or `<prosody>`).
     * @return A list of node groups. If everything fits, returns a single-element list.
     *   Atomic pronunciation nodes are not split; an oversized one remains a single
     *   node so the caller can reject it without changing its meaning.
     */
    internal fun chunkNodes(nodes: List<SsmlNode>, maxContentLength: Int): List<List<SsmlNode>> {
        require(maxContentLength > 0) { "Chunk length must be positive" }
        val splitNodes = nodes.flatMap { splitNode(it, maxContentLength) }
        val nodeLengths = splitNodes.map { renderNode(it).length }
        if (nodeLengths.sum() <= maxContentLength) return listOf(splitNodes)

        val groups = mutableListOf<List<SsmlNode>>()
        var currentGroup = mutableListOf<SsmlNode>()
        var currentLength = 0

        for ((i, node) in splitNodes.withIndex()) {
            val nodeLength = nodeLengths[i]
            if (currentLength + nodeLength > maxContentLength && currentGroup.isNotEmpty()) {
                groups += currentGroup.toList()
                currentGroup = mutableListOf()
                currentLength = 0
            }
            currentGroup += node
            currentLength += nodeLength
        }
        if (currentGroup.isNotEmpty()) {
            groups += currentGroup.toList()
        }

        return groups
    }

    private fun splitNode(node: SsmlNode, limit: Int): List<SsmlNode> {
        if (renderNode(node).length <= limit) return listOf(node)
        if (node is SsmlNode.Text) {
            val chunks = mutableListOf<SsmlNode>()
            var start = 0
            var index = 0
            var length = 0
            while (index < node.content.length) {
                val size = Character.charCount(node.content.codePointAt(index))
                val nextLength = escapeXml(node.content.substring(index, index + size)).length
                if (length > 0 && length + nextLength > limit) {
                    chunks += SsmlNode.Text(node.content.substring(start, index))
                    start = index
                    length = 0
                }
                length += nextLength
                index += size
            }
            if (start < index) chunks += SsmlNode.Text(node.content.substring(start, index))
            return chunks
        }
        val children = when (node) {
            is SsmlNode.Prosody -> node.children
            is SsmlNode.Emphasis -> node.children
            is SsmlNode.Sentence -> node.children
            is SsmlNode.Paragraph -> node.children
            else -> return listOf(node)
        }
        fun wrap(parts: List<SsmlNode>): SsmlNode = when (node) {
            is SsmlNode.Prosody -> node.copy(children = parts)
            is SsmlNode.Emphasis -> node.copy(children = parts)
            is SsmlNode.Sentence -> node.copy(children = parts)
            is SsmlNode.Paragraph -> node.copy(children = parts)
            else -> error("Not a container")
        }
        val contentLimit = limit - renderNode(wrap(emptyList())).length
        if (contentLimit <= 0) return listOf(node)
        return chunkNodes(children, contentLimit).map(::wrap)
    }

    private fun renderChildren(children: List<SsmlNode>): String {
        return children.joinToString("") { renderNode(it) }
    }

    private fun renderNode(node: SsmlNode): String = when (node) {
        is SsmlNode.Text -> escapeXml(node.content)

        is SsmlNode.Break -> "<break time=\"${node.timeMs}ms\"/>"

        is SsmlNode.Prosody -> {
            val attrs = buildList {
                node.rate?.let { add("rate=\"${escapeXml(it)}\"") }
                node.pitch?.let { add("pitch=\"${escapeXml(it)}\"") }
                node.volume?.let { add("volume=\"${escapeXml(it)}\"") }
            }.joinToString(" ")
            val inner = renderChildren(node.children)
            if (attrs.isNotEmpty()) "<prosody $attrs>$inner</prosody>" else inner
        }

        is SsmlNode.Emphasis -> "<emphasis level=\"${escapeXml(node.level)}\">${renderChildren(node.children)}</emphasis>"

        is SsmlNode.Phoneme -> "<phoneme alphabet=\"ipa\" ph=\"${escapeXml(node.ph)}\">${escapeXml(node.content)}</phoneme>"

        is SsmlNode.Sub -> "<sub alias=\"${escapeXml(node.alias)}\">${escapeXml(node.content)}</sub>"

        is SsmlNode.SayAs -> {
            val formatAttr = node.format?.let { " format=\"${escapeXml(it)}\"" } ?: ""
            "<say-as interpret-as=\"${escapeXml(node.interpretAs)}\"$formatAttr>${escapeXml(node.content)}</say-as>"
        }

        is SsmlNode.Sentence -> "<s>${renderChildren(node.children)}</s>"

        is SsmlNode.Paragraph -> "<p>${renderChildren(node.children)}</p>"
    }

    private fun escapeXml(text: String): String {
        // Fast path: skip allocation if no special chars
        if (text.indexOfFirst { it == '&' || it == '<' || it == '>' || it == '"' || it == '\'' } == -1) return text
        return buildString(text.length + 8) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
