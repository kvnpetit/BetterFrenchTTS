package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchFormats
import io.github.kvnpetit.betterfrenchtts.preprocessing.FrenchRegion
import io.github.kvnpetit.betterfrenchtts.spelling.FrenchCharMap
import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import kotlin.math.pow

/** A native speech request: text or a real silent utterance, plus engine controls. */
data class NativeSpeechStep(val text: String = "", val silenceMs: Long = 0, val rate: Float = 1f, val pitch: Float = 1f, val volume: Float = 1f)

/** Compiles the DSL to Android operations, never raw XML. Unsupported semantics fail explicitly. */
internal object NativeSpeechPlan {
    fun compile(nodes: List<SsmlNode>, region: FrenchRegion, autoChunk: Boolean = true): List<NativeSpeechStep> {
        val result = mutableListOf<NativeSpeechStep>()
        fun text(value: String, style: NativeSpeechStep) {
            if (value.isEmpty()) return
            val chunks = if (autoChunk) TextChunker.chunk(value) else listOf(value)
            require(chunks.all { it.length <= 3900 }) { "Speech exceeds the native input limit" }
            chunks.forEach { result += style.copy(text = it) }
        }
        fun visit(items: List<SsmlNode>, style: NativeSpeechStep) {
            for (node in items) when (node) {
                is SsmlNode.Text -> text(node.content, style)
                is SsmlNode.Sub -> text(node.alias, style)
                is SsmlNode.Phoneme -> throw IllegalArgumentException("IPA requires explicitly selected SSML playback and a compatible engine")
                is SsmlNode.Break -> {
                    require(node.timeMs >= 0) { "Pause cannot be negative" }
                    if (node.timeMs > 0) result += style.copy(silenceMs = node.timeMs.toLong())
                }
                is SsmlNode.Prosody -> visit(node.children, style.copy(
                    rate = node.rate?.let(::rate) ?: style.rate,
                    pitch = node.pitch?.let(::pitch) ?: style.pitch,
                    volume = node.volume?.let(::volume) ?: style.volume))
                is SsmlNode.Emphasis -> {
                    val multiplier = when (node.level) { "strong" -> 0.85f; "moderate" -> 0.95f; "reduced" -> 1.05f; else -> throw IllegalArgumentException("Unknown emphasis") }
                    visit(node.children, style.copy(rate = style.rate * multiplier))
                }
                is SsmlNode.Sentence -> { visit(node.children, style); result += style.copy(silenceMs = 180) }
                is SsmlNode.Paragraph -> { visit(node.children, style); result += style.copy(silenceMs = 350) }
                is SsmlNode.SayAs -> text(when (node.interpretAs) {
                    "cardinal" -> FrenchFormats.number(node.content, region)
                    "ordinal" -> FrenchFormats.ordinal(node.content.toLong(), region = region)
                    "date" -> FrenchFormats.date(node.content, node.format ?: "dmy", region)
                    "telephone" -> FrenchFormats.telephone(node.content, region)
                    "characters" -> spell(node.content)
                    else -> throw IllegalArgumentException("Unsupported native interpretation: ${node.interpretAs}")
                }, style)
            }
        }
        visit(nodes, NativeSpeechStep())
        return result
    }

    fun spell(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        .codePoints().toArray().joinToString(", ") { code ->
            if (code > Char.MAX_VALUE.code) "caractère unicode $code"
            else FrenchCharMap.resolve(code.toChar()) ?: when (val c = code.toChar()) {
                in '0'..'9' -> FrenchFormats.cardinal(c.digitToInt().toLong())
                else -> c.toString()
            }
        }

    private fun rate(value: String): Float {
        val number = when (value) { "x-slow" -> 0.5f; "slow" -> 0.75f; "medium" -> 1f; "fast" -> 1.25f; "x-fast" -> 1.5f; else -> value.removeSuffix("%").toFloat() / 100 }
        require(number.isFinite() && number in 0.1f..4f) { "Rate must be between 10% and 400%" }
        return number
    }
    private fun pitch(value: String): Float {
        val number = if (value.endsWith("st")) 2.0.pow(value.removeSuffix("st").toDouble() / 12).toFloat()
        else when (value) { "x-low" -> 0.5f; "low" -> 0.75f; "medium" -> 1f; "high" -> 1.25f; "x-high" -> 1.5f; else -> throw IllegalArgumentException("Use semitones or a named pitch") }
        require(number.isFinite() && number in 0.1f..4f) { "Pitch outside supported range" }
        return number
    }
    private fun volume(value: String): Float = when (value) {
        "silent" -> 0f; "x-soft" -> 0.2f; "soft" -> 0.5f; "medium" -> 0.8f; "loud", "x-loud" -> 1f
        else -> throw IllegalArgumentException("Use a named volume")
    }
}
