package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import org.junit.Assert.*
import org.junit.Test

class PronunciationDictionaryTest {
    @Test fun wholeWordsDoNotMatchInsideOtherWords() {
        val dictionary = PronunciationDictionary().apply { add(PronunciationRule.Alias("chat", "félin")) }
        assertEquals(listOf(SsmlNode.Sub("chat", "félin"), SsmlNode.Text(" château achat")), dictionary.nodes("chat château achat"))
    }
    @Test fun caseSensitiveAndSubstringRulesAreOptIn() {
        val dictionary = PronunciationDictionary().apply { add(PronunciationRule.Alias("API", "interface", wholeWord = false, ignoreCase = false)) }
        assertEquals(listOf(SsmlNode.Text("api "), SsmlNode.Sub("API", "interface"), SsmlNode.Text("s")), dictionary.nodes("api APIs"))
    }
    @Test fun longestExpressionWinsAndMetacharactersAreLiteral() {
        val dictionary = PronunciationDictionary().apply {
            add(PronunciationRule.Alias("Saint", "saint")); add(PronunciationRule.Alias("Saint-Étienne", "ville"))
            add(PronunciationRule.Alias("C++", "cé plus plus"))
        }
        assertEquals(listOf(SsmlNode.Sub("Saint-Étienne", "ville"), SsmlNode.Text(" "), SsmlNode.Sub("C++", "cé plus plus")), dictionary.nodes("Saint-Étienne C++"))
    }
    @Test fun exportRoundTripsUnicodeAndControlCharacters() {
        val original = PronunciationDictionary().apply { add(PronunciationRule.Alias("Émile", "émile\t\n")); add(PronunciationRule.Ipa("mot", "mo")) }
        val copy = PronunciationDictionary().apply { import(original.export()) }
        assertEquals(original.rules(), copy.rules())
    }
    @Test fun invalidImportDoesNotPartiallyReplaceData() {
        val dictionary = PronunciationDictionary().apply { add(PronunciationRule.Alias("mot", "autre")) }
        val before = dictionary.export()
        assertThrows(IllegalArgumentException::class.java) { dictionary.import(before + "\ninvalid", replace = true) }
        assertEquals(before, dictionary.export())
        assertThrows(IllegalArgumentException::class.java) { dictionary.add(PronunciationRule.Alias("", "oops")) }
    }
}
