package io.github.kvnpetit.betterfrenchtts

import org.junit.Assert.*
import org.junit.Test

class TextChunkerTest {
    @Test fun emptyAndBoundaryInputsArePreserved() {
        for (text in listOf("", " ", "a".repeat(3900))) {
            assertEquals(listOf(text), TextChunker.chunk(text))
        }
    }

    @Test fun paragraphWinsOverLaterWordBoundary() {
        val first = "a".repeat(2200)
        val rest = "b ".repeat(2000)
        val chunks = TextChunker.chunk("$first\n\n$rest")
        assertEquals(first, chunks.first())
        assertTrue(chunks.all { it.length <= 3900 })
    }

    @Test fun sentenceAndClauseBoundariesKeepPunctuation() {
        for (separator in listOf(". ", "! ", "? ", ", ", "; ")) {
            val first = "a".repeat(2500) + separator.trim()
            assertEquals(first, TextChunker.chunk(first + " " + "b".repeat(2500)).first())
        }
    }

    @Test fun hardCutsPreserveSupplementaryUnicodeCharacters() {
        val text = "a" + "😀".repeat(4500)
        val chunks = TextChunker.chunk(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 3900 })
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
    }
}
