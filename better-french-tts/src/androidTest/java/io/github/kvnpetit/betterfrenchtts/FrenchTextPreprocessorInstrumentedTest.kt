package io.github.kvnpetit.betterfrenchtts

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android's ICU regex engine, which differs from the host JDK. */
@RunWith(AndroidJUnit4::class)
class FrenchTextPreprocessorInstrumentedTest {
    @Test
    fun plainTextInitializesPreprocessorWithoutCrashing() {
        assertEquals("Bonjour", FrenchTextPreprocessor.process("Bonjour"))
    }

    @Test
    fun romanNumeralsPreserveTheirContextOnAndroid() {
        val cases = mapOf(
            "Louis XIV" to "Louis quatorze",
            "chapitre IV, tome II" to "chapitre quatre, tome deux",
            "Émile XXI" to "Émile vingt-et-un",
            "Louis XIVe" to "Louis quatorze",
            "XIV" to "XIV",
            "Louis XIVabc" to "Louis XIVabc"
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, FrenchTextPreprocessor.process(input))
        }
    }
}
