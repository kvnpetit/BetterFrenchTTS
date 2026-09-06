package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder
import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchRegion
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import org.junit.Assert.*
import org.junit.Test

class NativeSpeechPlanTest {
    @Test fun adjacentIdenticalStylesAndAliasesStayInOneUtterance() {
        val steps = plan { text("Les "); sub("amis", "amis"); prosody(rate = "medium") { text(" arrivent.") } }
        assertEquals(1, steps.size)
        assertEquals("Les amis arrivent.", steps.single().text)
    }
    @Test fun explicitSilenceAndDifferentControlsAreNotMerged() {
        val steps = plan { text("a"); pause(25); text("b"); slow { text("c") } }
        assertEquals(4, steps.size)
        assertEquals(25L, steps[1].silenceMs)
    }
    @Test fun mergedFragmentsStillRespectLengthAndUnicodeLimits() {
        val steps = plan { repeat(5000) { text("é😀") } }
        assertTrue(steps.all { it.text.length <= 3900 })
        assertEquals("é😀".repeat(5000), steps.joinToString("") { it.text })
    }
    private fun plan(block: SpeechBuilder.() -> Unit) = NativeSpeechPlan.compile(SpeechBuilder().apply(block).nodes, FrenchRegion.FRANCE)
    @Test fun textIsNotXmlAndAliasIsSpokenDirectly() {
        assertEquals(listOf("A & <B> Oua-ouei"), plan { text("A & <B> "); sub("Huawei", "Oua-ouei") }.map { it.text })
    }
    @Test fun pausesAndProsodyBecomeNativeControls() {
        val steps = plan { slow { text("lent"); pause(300) }; text("normal") }
        assertEquals(0.75f, steps[0].rate)
        assertEquals(300L, steps[1].silenceMs)
        assertEquals(1f, steps[2].rate)
    }
    @Test fun typedDataIsRenderedInFrench() {
        val steps = plan { number("22"); pause(1); ordinal("22"); pause(1); date("01/09/2026"); pause(1); telephone("06 01 02 03 04") }.filter { it.silenceMs == 0L }
        assertEquals("vingt-deux", steps[0].text)
        assertEquals("vingt-deuxième", steps[1].text)
        assertTrue(steps[2].text.startsWith("premier septembre"))
        assertTrue(steps[3].text.startsWith("zéro six"))
    }
    @Test fun unsupportedIpaAndInvalidControlsFailExplicitly() {
        assertThrows(IllegalArgumentException::class.java) { plan { phoneme("mot", "mo") } }
        assertThrows(IllegalArgumentException::class.java) { plan { pause(-1) } }
        assertThrows(IllegalArgumentException::class.java) { plan { prosody(rate = "NaN%") { text("x") } } }
    }
    @Test fun spellingNormalizesAccentsAndPreservesCodePoints() {
        assertEquals(plan { spellOut("é") }, plan { spellOut("e\u0301") })
        val text = plan { spellOut("😀") }.single().text
        assertEquals("caractère unicode 128512", text)
    }
    @Test fun longAliasesAreSplitWithoutMarkup() {
        val source = "&😀".repeat(4000)
        val steps = plan { sub("mot", source) }
        assertTrue(steps.all { it.text.length <= 3900 })
        assertEquals(source, steps.joinToString("") { it.text })
    }
    @Test fun nestedStylesAreRestored() {
        val steps = plan { slow { text("a"); fast { text("b") }; text("c") }; text("d") }
        assertEquals(listOf(0.75f, 1.25f, 0.75f, 1f), steps.map { it.rate })
    }
}
