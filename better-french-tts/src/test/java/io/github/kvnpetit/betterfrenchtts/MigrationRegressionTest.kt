package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder
import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationRegressionTest {
    @Test
    fun romanNumeralsKeepContextAndPunctuation() {
        assertEquals(
            "Louis quatorze, chapitre quatre, tome deux",
            FrenchTextPreprocessor.process("Louis XIV, chapitre IV, tome II")
        )
    }

    @Test
    fun shortFrenchTextIsPreserved() {
        assertEquals(listOf("Bonjour, été !"), TextChunker.chunk("Bonjour, été !"))
    }

    @Test
    fun longUnbrokenTextIsSplitWithoutLosingCharacters() {
        val text = "é".repeat(10000)
        val chunks = TextChunker.chunk(text)
        assertTrue(chunks.all { it.length <= 3900 })
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun migratedDslRendersEscapedTextAndPause() {
        val speech = SpeechBuilder().apply {
            text("Bonjour & <salut>")
            pause(250)
        }
        assertEquals(
            "<speak>Bonjour &amp; &lt;salut&gt;<break time=\"250ms\"/></speak>",
            SsmlRenderer.render(speech.nodes)
        )
    }

    @Test
    fun migratedPreprocessorExpandsFrenchTitles() {
        assertEquals("Monsieur Dupont", FrenchTextPreprocessor.process("M. Dupont"))
    }
}
