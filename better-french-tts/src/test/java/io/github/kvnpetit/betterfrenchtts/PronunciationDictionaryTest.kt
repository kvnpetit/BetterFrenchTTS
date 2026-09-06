package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import org.junit.Assert.*
import org.junit.Test

class PronunciationDictionaryTest {
    @Test
    fun cachedRulesAreInvalidatedByEveryMutation() {
        val dictionary = PronunciationDictionary()
        fun alias() = (dictionary.nodes("mot").single() as SsmlNode.Sub).alias
        dictionary.add(PronunciationRule.Alias("mot", "un"))
        repeat(10) { assertEquals("un", alias()) }
        dictionary.add(PronunciationRule.Alias("mot", "deux"))
        assertEquals("deux", alias())
        dictionary.remove("mot")
        assertEquals(listOf(SsmlNode.Text("mot")), dictionary.nodes("mot"))
        dictionary.add(PronunciationRule.Alias("mot", "trois"))
        assertEquals("trois", alias())
        dictionary.import("better-french-tts-dictionary-v1\n", replace = true)
        assertEquals(listOf(SsmlNode.Text("mot")), dictionary.nodes("mot"))
    }

    @Test
    fun literalCaptureCharactersDoNotConfuseAlternativeSelection() {
        val dictionary =
            PronunciationDictionary().apply {
                add(PronunciationRule.Alias("a(b)", "parenthèses"))
                add(PronunciationRule.Alias("c[d]", "crochets"))
            }
        assertEquals(
            listOf(
                SsmlNode.Sub("a(b)", "parenthèses"),
                SsmlNode.Text(" "),
                SsmlNode.Sub("c[d]", "crochets"),
            ),
            dictionary.nodes("a(b) c[d]"),
        )
    }

    @Test
    fun manyRulesPreserveMatchingAcrossRepeatedReads() {
        val dictionary =
            PronunciationDictionary().apply {
                repeat(500) { add(PronunciationRule.Alias("terme$it", "lecture$it")) }
            }
        repeat(20) {
            assertEquals(
                listOf(SsmlNode.Sub("terme499", "lecture499")),
                dictionary.nodes("terme499"),
            )
        }
    }

    @Test
    fun wholeWordsDoNotMatchInsideOtherWords() {
        val dictionary =
            PronunciationDictionary().apply { add(PronunciationRule.Alias("chat", "félin")) }
        assertEquals(
            listOf(SsmlNode.Sub("chat", "félin"), SsmlNode.Text(" château achat")),
            dictionary.nodes("chat château achat"),
        )
    }

    @Test
    fun caseSensitiveAndSubstringRulesAreOptIn() {
        val dictionary =
            PronunciationDictionary().apply {
                add(
                    PronunciationRule.Alias(
                        "API",
                        "interface",
                        wholeWord = false,
                        ignoreCase = false,
                    )
                )
            }
        assertEquals(
            listOf(SsmlNode.Text("api "), SsmlNode.Sub("API", "interface"), SsmlNode.Text("s")),
            dictionary.nodes("api APIs"),
        )
    }

    @Test
    fun longestExpressionWinsAndMetacharactersAreLiteral() {
        val dictionary =
            PronunciationDictionary().apply {
                add(PronunciationRule.Alias("Saint", "saint"))
                add(PronunciationRule.Alias("Saint-Étienne", "ville"))
                add(PronunciationRule.Alias("C++", "cé plus plus"))
            }
        assertEquals(
            listOf(
                SsmlNode.Sub("Saint-Étienne", "ville"),
                SsmlNode.Text(" "),
                SsmlNode.Sub("C++", "cé plus plus"),
            ),
            dictionary.nodes("Saint-Étienne C++"),
        )
    }

    @Test
    fun exportRoundTripsUnicodeAndControlCharacters() {
        val original =
            PronunciationDictionary().apply {
                add(PronunciationRule.Alias("Émile", "émile\t\n"))
                add(PronunciationRule.Ipa("mot", "mo"))
            }
        val copy = PronunciationDictionary().apply { import(original.export()) }
        assertEquals(original.rules(), copy.rules())
    }

    @Test
    fun invalidImportDoesNotPartiallyReplaceData() {
        val dictionary =
            PronunciationDictionary().apply { add(PronunciationRule.Alias("mot", "autre")) }
        val before = dictionary.export()
        assertThrows(IllegalArgumentException::class.java) {
            dictionary.import(before + "\ninvalid", replace = true)
        }
        assertEquals(before, dictionary.export())
        assertThrows(IllegalArgumentException::class.java) {
            dictionary.add(PronunciationRule.Alias("", "oops"))
        }
    }
}
