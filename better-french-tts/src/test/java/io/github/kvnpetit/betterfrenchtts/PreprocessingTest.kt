package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor.process
import org.junit.Assert.assertEquals
import org.junit.Test

class PreprocessingTest {
    @Test
    fun emptyAndOrdinaryTextStayUnchanged() {
        for (text in listOf("", "Bonjour, été !", "XIV", "Louis XIVabc")) assertEquals(
            text,
            process(text),
        )
    }

    @Test
    fun titlesRespectWordBoundaries() {
        assertEquals("Madame Dupont et Docteur Martin", process("Mme Dupont et Dr Martin"))
        assertEquals("Drive", process("Drive"))
    }

    @Test
    fun ordinalsHandleGenderAndUnsupportedNumbers() {
        assertEquals(
            "premier, première, deuxième, vingt-et-unième, vingt-deuxième",
            process("1er, 1ère, 2ème, 21e, 22e"),
        )
    }

    @Test
    fun hoursHandleMinutesAndWholeHours() {
        assertEquals("14 heures 30 puis 9 heures et 18 heures", process("14h30 puis 9h00 et 18H"))
    }

    @Test
    fun unitsHandleSingularPluralAndCompoundNames() {
        assertEquals("1 kilomètre puis 3 kilomètres par heure", process("1 km puis 3 km/h"))
        assertEquals("1,0 kilogramme et 2,5 kilogrammes", process("1,0 kg et 2,5 kg"))
    }

    @Test
    fun currenciesAndPercentagesAreExpanded() {
        assertEquals("15 euros et 50 pourcent", process("15€ et 50%"))
    }

    @Test
    fun centuriesUseFrenchOrdinals() {
        assertEquals(
            "premier siècle, quatrième siècle, vingtième siècle",
            process("Ie siècle, IVe siècle, XXe siècle"),
        )
    }
}
