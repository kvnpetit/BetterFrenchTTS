package com.github.kvnpetit.betterfrenchtts

/**
 * Splits long text into chunks that fit within Android TTS's ~4000 character limit.
 * Splits at natural boundaries (paragraphs > sentences > clauses > words).
 */
internal object TextChunker {

    private const val MAX_TTS_LENGTH = 3900

    fun chunk(text: String): List<String> {
        if (text.length <= MAX_TTS_LENGTH) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_TTS_LENGTH) {
                chunks += remaining
                break
            }

            val candidate = remaining.substring(0, MAX_TTS_LENGTH)
            val splitIndex = findBestSplitPoint(candidate)
            chunks += remaining.substring(0, splitIndex).trimEnd()
            remaining = remaining.substring(splitIndex).trimStart()
        }

        return chunks
    }

    private fun findBestSplitPoint(text: String): Int {
        // Paragraph boundary
        val paragraphBreak = text.lastIndexOf("\n\n")
        if (paragraphBreak > text.length / 2) return paragraphBreak + 2

        // Sentence boundary (. ! ?)
        val sentenceEnd = maxOf(
            text.lastIndexOf(". "),
            text.lastIndexOf("! "),
            text.lastIndexOf("? ")
        )
        if (sentenceEnd > text.length / 2) return sentenceEnd + 2

        // Clause boundary (, ;)
        val clauseEnd = maxOf(
            text.lastIndexOf(", "),
            text.lastIndexOf("; ")
        )
        if (clauseEnd > text.length / 2) return clauseEnd + 2

        // Word boundary
        val spaceIndex = text.lastIndexOf(' ')
        if (spaceIndex > text.length / 2) return spaceIndex + 1

        // Hard cut as last resort
        return text.length
    }
}
