package io.github.kvnpetit.betterfrenchtts.ssml

import org.junit.Assert.assertEquals
import org.junit.Test

class SsmlRendererTest {
    @Test
    fun longNestedTextIsSplitIntoValidBoundedXmlWithoutLosingContent() {
        val text = "été & <😀> ".repeat(600)
        val nodes = listOf(SsmlNode.Paragraph(listOf(SsmlNode.Prosody(
            rate = "slow", children = listOf(SsmlNode.Text(text))
        ))))
        val groups = SsmlRenderer.chunkNodes(nodes, 300)
        val restored = groups.joinToString("") { group ->
            val xml = SsmlRenderer.render(group)
            org.junit.Assert.assertTrue(xml.length <= 315)
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(java.io.ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                .documentElement.textContent
        }
        assertEquals(text, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroChunkLimitIsRejected() {
        SsmlRenderer.chunkNodes(listOf(SsmlNode.Text("test")), 0)
    }

    @Test
    fun atomicPronunciationIsNotSilentlyRewritten() {
        val node = SsmlNode.Sub("mot", "long".repeat(100))
        assertEquals(listOf(listOf(node)), SsmlRenderer.chunkNodes(listOf(node), 20))
    }
    @Test
    fun allAttributesAreXmlEscaped() {
        val value = "\"<&'"
        val nodes = listOf(
            SsmlNode.Prosody(rate = value, pitch = value, volume = value),
            SsmlNode.Emphasis(level = value),
            SsmlNode.SayAs(interpretAs = value, content = "texte", format = value),
            SsmlNode.Phoneme(content = "mot", ph = value)
        )
        val xml = SsmlRenderer.render(nodes)
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(java.io.ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        assertEquals(value, document.getElementsByTagName("prosody").item(0).attributes.getNamedItem("rate").nodeValue)
        assertEquals(value, document.getElementsByTagName("say-as").item(0).attributes.getNamedItem("format").nodeValue)
    }

    @Test
    fun emptyNodesAndUnstyledProsodyRenderWithoutExtraTags() {
        assertEquals("<speak></speak>", SsmlRenderer.render(emptyList()))
        assertEquals("<speak>bonjour</speak>", SsmlRenderer.render(listOf(
            SsmlNode.Prosody(children = listOf(SsmlNode.Text("bonjour")))
        )))
    }

    @Test
    fun chunkingMeasuresEscapedLength() {
        val node = SsmlNode.Text("&")
        assertEquals(listOf(listOf(node), listOf(node)), SsmlRenderer.chunkNodes(listOf(node, node), 5))
    }
    @Test
    fun substitutionEscapesTextAndAttributeValues() {
        assertEquals(
            "<speak><sub alias=\"A &amp; &quot;B&quot;\">&lt;mot&gt;</sub></speak>",
            SsmlRenderer.render(listOf(SsmlNode.Sub("<mot>", "A & \"B\"")))
        )
    }

    @Test
    fun nestedProsodyPreservesStructure() {
        val nodes = listOf(SsmlNode.Paragraph(listOf(
            SsmlNode.Prosody(rate = "slow", children = listOf(SsmlNode.Text("Bonjour"))),
            SsmlNode.Break(200)
        )))
        assertEquals(
            "<speak><p><prosody rate=\"slow\">Bonjour</prosody><break time=\"200ms\"/></p></speak>",
            SsmlRenderer.render(nodes)
        )
    }

    @Test
    fun chunkingPreservesNodeOrderAtTheLengthBoundary() {
        val first = SsmlNode.Text("Bonjour")
        val second = SsmlNode.Text("Salut")
        val third = SsmlNode.Text("Au revoir")
        assertEquals(
            listOf(listOf(first, second), listOf(third)),
            SsmlRenderer.chunkNodes(listOf(first, second, third), maxContentLength = 12)
        )
    }
}
