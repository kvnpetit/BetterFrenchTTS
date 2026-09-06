package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchTextPreprocessor
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlRenderer

/** Pure text preparation shared by preview, playback, queues and file export. */
internal class SpeechPreparation(
    private val config: BetterFrenchTts.Config,
    private val dictionary: PronunciationDictionary,
) {
    private companion object {
        const val SPEAK_OVERHEAD = 15
    }

    fun wrapInProsody(preset: SpeechPreset, children: List<SsmlNode>): List<SsmlNode> {
        return listOf(
            SsmlNode.Prosody(
                rate = preset.rate,
                pitch = preset.pitch,
                volume = preset.volume,
                children = children,
            )
        )
    }

    fun renderTextChunks(text: String, preset: SpeechPreset): List<String> {
        return TextChunker.chunk(text).flatMap { chunk ->
            SsmlRenderer.chunkNodes(
                    wrapInProsody(preset, textToNodes(chunk)),
                    3900 - SPEAK_OVERHEAD,
                )
                .map(SsmlRenderer::render)
        }
    }

    fun computeSsmlTextOffset(preset: SpeechPreset): Int {
        return SsmlRenderer.render(wrapInProsody(preset, emptyList())).length -
            "</prosody></speak>".length
    }

    fun computeProsodyOverhead(preset: SpeechPreset): Int {
        return SsmlRenderer.render(wrapInProsody(preset, emptyList())).length - SPEAK_OVERHEAD
    }

    fun preprocess(text: String): String {
        return if (config.preprocessText) FrenchTextPreprocessor.process(text, config.normalization)
        else text
    }

    fun textToNodes(text: String): List<SsmlNode> = dictionary.nodes(text)

    fun prepareNativeNodes(nodes: List<SsmlNode>): List<SsmlNode> =
        nodes.flatMap { node ->
            when (node) {
                is SsmlNode.Text -> textToNodes(preprocess(node.content))
                is SsmlNode.Prosody ->
                    listOf(node.copy(children = prepareNativeNodes(node.children)))
                is SsmlNode.Emphasis ->
                    listOf(node.copy(children = prepareNativeNodes(node.children)))
                is SsmlNode.Paragraph ->
                    listOf(node.copy(children = prepareNativeNodes(node.children)))
                is SsmlNode.Sentence ->
                    listOf(node.copy(children = prepareNativeNodes(node.children)))
                else -> listOf(node)
            }
        }
}
