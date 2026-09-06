package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FrenchCorpusTest(private val input: String, private val expected: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): Collection<Array<String>> {
            val stream =
                requireNotNull(
                    FrenchCorpusTest::class.java.getResourceAsStream("/french-corpus.tsv")
                )
            return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines
                    .filter { it.isNotBlank() && !it.startsWith('#') }
                    .map { line ->
                        val fields = line.split('\t')
                        require(fields.size == 2)
                        arrayOf(fields[0], fields[1])
                    }
                    .toList()
                    .also { require(it.size == 200) }
            }
        }
    }

    @Test
    fun normalizationMatchesReference() {
        assertEquals(expected, FrenchTextPreprocessor.process(input))
    }
}
