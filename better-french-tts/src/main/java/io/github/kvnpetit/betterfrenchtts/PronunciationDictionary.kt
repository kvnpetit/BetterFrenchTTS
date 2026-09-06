package io.github.kvnpetit.betterfrenchtts

import io.github.kvnpetit.betterfrenchtts.ssml.SsmlNode
import java.util.Base64
import java.util.Locale

/** Thread-safe dictionary. Export is versioned UTF-8/base64 TSV, never executable rules. */
class PronunciationDictionary {
    private val entries = linkedMapOf<String, PronunciationRule>()

    @Synchronized fun add(rule: PronunciationRule) {
        require(rule.word.isNotBlank()) { "Pronunciation word cannot be blank" }
        entries[rule.word.lowercase(Locale.ROOT)] = rule
    }
    @Synchronized fun remove(word: String) { entries.remove(word.lowercase(Locale.ROOT)) }
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun rules(): List<PronunciationRule> = entries.values.toList()

    internal fun nodes(text: String): List<SsmlNode> {
        val rules = rules().sortedByDescending { it.word.length }
        if (rules.isEmpty()) return listOf(SsmlNode.Text(text))
        val patterns = rules.map { rule ->
            val literal = Regex.escape(rule.word)
            val pattern = (if (rule.wholeWord) "(?<![\\p{L}\\p{M}\\p{N}_])" else "") +
                (if (rule.ignoreCase) "(?iu:$literal)" else literal) +
                (if (rule.wholeWord) "(?![\\p{L}\\p{M}\\p{N}_])" else "")
            Regex(pattern)
        }
        val regex = Regex(patterns.joinToString("|") { "(?:${it.pattern})" })
        val output = mutableListOf<SsmlNode>()
        var end = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > end) output += SsmlNode.Text(text.substring(end, match.range.first))
            val index = patterns.indexOfFirst { it.find(text, match.range.first)?.range == match.range }
            output += when (val rule = rules[index]) {
                is PronunciationRule.Alias -> SsmlNode.Sub(match.value, rule.readAs)
                is PronunciationRule.Ipa -> SsmlNode.Phoneme(match.value, rule.ipa)
            }
            end = match.range.last + 1
        }
        if (end < text.length) output += SsmlNode.Text(text.substring(end))
        return output
    }

    fun export(): String {
        fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        return "better-french-tts-dictionary-v1\n" + rules().joinToString("\n") { rule ->
            val value = when (rule) { is PronunciationRule.Alias -> rule.readAs; is PronunciationRule.Ipa -> rule.ipa }
            listOf(if (rule is PronunciationRule.Alias) "alias" else "ipa", encode(rule.word), encode(value), rule.wholeWord, rule.ignoreCase).joinToString("\t")
        }
    }

    /** Validate the entire document before modifying the dictionary. */
    @Synchronized fun import(data: String, replace: Boolean = false) {
        require(data.length <= 1_000_000) { "Dictionary file too large" }
        val lines = data.lineSequence().toList()
        require(lines.firstOrNull() == "better-french-tts-dictionary-v1") { "Unsupported dictionary format" }
        fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        val parsed = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val fields = line.split('\t')
            require(fields.size == 5) { "Invalid dictionary row" }
            val word = decode(fields[1])
            require(word.isNotBlank()) { "Empty dictionary word" }
            val whole = fields[3].toBooleanStrict()
            val ignore = fields[4].toBooleanStrict()
            when (fields[0]) {
                "alias" -> PronunciationRule.Alias(word, decode(fields[2]), whole, ignore)
                "ipa" -> PronunciationRule.Ipa(word, decode(fields[2]), whole, ignore)
                else -> throw IllegalArgumentException("Unknown pronunciation kind")
            }
        }
        if (replace) entries.clear()
        parsed.forEach(::add)
    }
}
