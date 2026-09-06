package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.preprocessing.*
import org.junit.Assert.*
import org.junit.Test

class FrenchFormatsTest {
    @Test fun feminineQuantitiesAgreeWithoutChangingInternalThousands() {
        assertEquals("vingt et une heures", FrenchFormats.duration(21 * 3600))
        assertEquals("quatre-vingt-une livres sterling", FrenchFormats.money("81", "GBP"))
        assertEquals("une heure et vingt et une minutes", FrenchFormats.duration(81 * 60))
        assertEquals("cent une livres sterling", FrenchFormats.money("101", "GBP"))
        assertEquals("vingt et un mille heures", FrenchFormats.duration(21000L * 3600))
    }
    @Test fun quantitiesBelowTwoAndStandardFirstOrdinalAreSingular() {
        assertEquals("0,5 mètre et 1,5 euro et -1 kilogramme", FrenchTextPreprocessor.process("0,5 m et 1,5 € et -1 kg"))
        assertEquals("première fois", FrenchTextPreprocessor.process("1re fois"))
    }
    @Test fun scientificNotationAndVersionIdentifiersAreNotPartiallyExpanded() {
        val input = "1e3€ 1.2e-3m v1.5€ 1.2.3€"
        assertEquals(input, FrenchTextPreprocessor.process(input))
    }
    @Test fun cardinalAgreementsAndScales() {
        mapOf(0L to "zéro", 21L to "vingt et un", 71L to "soixante et onze", 80L to "quatre-vingts",
            81L to "quatre-vingt-un", 91L to "quatre-vingt-onze", 200L to "deux cents", 201L to "deux cent un",
            3000L to "trois mille", 80000L to "quatre-vingt mille", 200000L to "deux cent mille",
            2000000L to "deux millions", -42L to "moins quarante-deux").forEach { (n, word) -> assertEquals(word, FrenchFormats.cardinal(n)) }
    }
    @Test fun regionalConventionsAreExplicit() {
        assertEquals("septante et un", FrenchFormats.cardinal(71, FrenchRegion.BELGIUM))
        assertEquals("nonante", FrenchFormats.cardinal(90, FrenchRegion.SWITZERLAND))
        assertEquals("quatre-vingt-dix", FrenchFormats.cardinal(90, FrenchRegion.CANADA))
    }
    @Test fun ordinalsAreNotLimitedToATable() {
        mapOf(1L to "premier", 5L to "cinquième", 9L to "neuvième", 22L to "vingt-deuxième",
            80L to "quatre-vingtième", 200L to "deux-centième", 1000L to "millième").forEach { (n, word) -> assertEquals(word, FrenchFormats.ordinal(n)) }
        assertEquals("première", FrenchFormats.ordinal(1, true))
    }
    @Test fun decimalsPreserveZeros() {
        assertEquals("moins zéro virgule zéro cinq", FrenchFormats.number("-0,05"))
        assertEquals("mille deux cent trente-quatre virgule cinq zéro", FrenchFormats.number("1\u202f234,50"))
    }
    @Test fun moneyHasUnitsAndCents() {
        assertEquals("un euro et un centime", FrenchFormats.money("1,01"))
        assertEquals("moins deux euros et cinquante centimes", FrenchFormats.money("-2,50"))
        assertEquals("trois livres sterling et deux pence", FrenchFormats.money("3.02", "GBP"))
        assertEquals("une livre sterling", FrenchFormats.money("1", "GBP"))
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.money("1e999999999") }
    }
    @Test fun explicitDatesValidateCalendar() {
        assertEquals("premier septembre deux mille vingt-six", FrenchFormats.date("01/09/2026"))
        assertEquals(FrenchFormats.date("01/09/2026"), FrenchFormats.date("2026-09-01", "ymd"))
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.date("31/02/2026") }
    }
    @Test fun telephonesPreserveLeadingZeros() {
        assertEquals("zéro six, zéro un, zéro deux, zéro trois, zéro quatre", FrenchFormats.telephone("06 01 02 03 04"))
        assertTrue(FrenchFormats.telephone("+33 6 01 02 03 04").startsWith("plus trois trois"))
    }
    @Test fun durationsRespectGenderAndZero() {
        assertEquals("une heure et une minute et une seconde", FrenchFormats.duration(3661))
        assertEquals("zéro seconde", FrenchFormats.duration(0))
    }
    @Test fun invalidInputsFailWithoutApproximation() {
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.cardinal(Long.MIN_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.ordinal(0) }
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.money("1.001") }
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.telephone("call me") }
        assertThrows(IllegalArgumentException::class.java) { FrenchFormats.duration(-1) }
    }
    @Test fun previewExplainsChangesAndProtectsIdentifiers() {
        val preview = FrenchTextPreprocessor.preview("M. Dupont paie 1€ à 1h.")
        assertEquals("Monsieur Dupont paie 1 euro à 1 heure.", preview.text)
        assertEquals(listOf("abbreviations", "times", "currencies"), preview.transformations.map { it.rule })
        val identifiers = "https://exemple.fr/1h test1h contact@exemple.fr `1h`"
        assertEquals(identifiers, FrenchTextPreprocessor.process(identifiers))
        assertEquals("1h", FrenchTextPreprocessor.process("1h", FrenchTextPreprocessor.Options(times = false)))
    }
}
