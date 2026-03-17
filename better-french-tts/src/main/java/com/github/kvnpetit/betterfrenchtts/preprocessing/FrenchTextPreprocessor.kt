package com.github.kvnpetit.betterfrenchtts.preprocessing

/**
 * Intelligent French text preprocessor that normalizes text before TTS synthesis.
 *
 * Handles common French-specific patterns that Android TTS engines often mispronounce:
 * - Abbreviations (M. -> Monsieur, Mme -> Madame, etc.)
 * - Ordinal suffixes (1er -> premier, 2ème -> deuxième, etc.)
 * - Time formats (14h30 -> 14 heures 30)
 * - Units with correct plural forms (3 km -> 3 kilomètres)
 * - Roman numerals in context (Louis XIV -> Louis quatorze)
 * - Currency symbols (15€ -> 15 euros)
 * - Percentage signs (50% -> 50 pourcent)
 *
 * @see com.github.kvnpetit.betterfrenchtts.BetterFrenchTts
 */
object FrenchTextPreprocessor {

    /**
     * Applies all French text normalization rules to [text].
     *
     * Rules are applied in a specific order to avoid conflicts
     * (e.g. abbreviations before units, ordinals before plain numbers).
     *
     * @param text The raw French text to preprocess.
     * @return The normalized text ready for TTS synthesis.
     */
    fun process(text: String): String {
        var result = text
        result = expandAbbreviations(result)
        result = expandOrdinals(result)
        result = expandTime(result)
        result = expandCurrency(result)
        result = expandPercentage(result)
        result = expandUnits(result)
        result = expandRomanNumerals(result)
        return result
    }

    // -- Abbreviations --

    private val ABBREVIATIONS = listOf(
        // Titres de civilité
        "\\bM\\.(?=\\s)" to "Monsieur",
        "\\bMM\\.(?=\\s)" to "Messieurs",
        "\\bMme\\b" to "Madame",
        "\\bMmes\\b" to "Mesdames",
        "\\bMlle\\b" to "Mademoiselle",
        "\\bMlles\\b" to "Mesdemoiselles",
        // Titres professionnels
        "\\bDr\\b" to "Docteur",
        "\\bPr\\b" to "Professeur",
        "\\bMe\\b(?=\\s+[A-Z])" to "Maître",
        "\\bMgr\\b" to "Monseigneur",
        // Saints
        "\\bSt\\b" to "Saint",
        "\\bSte\\b" to "Sainte",
        // Adresses
        "\\bbd\\b" to "boulevard",
        "\\bav\\.(?=\\s)" to "avenue",
        "\\bpl\\.(?=\\s)" to "place",
        // Locutions
        "\\betc\\.?" to "et cetera",
        "\\bc\\.-à-d\\.?" to "c'est-à-dire",
        "\\bN\\.B\\.?" to "nota bene",
        "\\bP\\.S\\.?" to "post-scriptum",
    ).map { (pattern, replacement) -> Regex(pattern) to replacement }

    private fun expandAbbreviations(text: String): String {
        var result = text
        for ((regex, replacement) in ABBREVIATIONS) {
            result = regex.replace(result, replacement)
        }
        return result
    }

    // -- Ordinals --

    private val ORDINAL_REGEX = Regex("""(\d+)(er|ère|ème|e|ème|ème)(?:\b|(?=\s|[.,;:!?]))""")

    private val ORDINAL_WORDS = mapOf(
        1 to "premier",
        2 to "deuxième",
        3 to "troisième",
        4 to "quatrième",
        5 to "cinquième",
        6 to "sixième",
        7 to "septième",
        8 to "huitième",
        9 to "neuvième",
        10 to "dixième",
        11 to "onzième",
        12 to "douzième",
        13 to "treizième",
        14 to "quatorzième",
        15 to "quinzième",
        16 to "seizième",
        17 to "dix-septième",
        18 to "dix-huitième",
        19 to "dix-neuvième",
        20 to "vingtième",
        21 to "vingt-et-unième",
        30 to "trentième",
        40 to "quarantième",
        50 to "cinquantième",
        100 to "centième",
        1000 to "millième",
    )

    private fun expandOrdinals(text: String): String {
        return ORDINAL_REGEX.replace(text) { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val suffix = match.groupValues[2]
            val feminine = suffix == "ère"
            val word = ORDINAL_WORDS[number]
            if (word != null) {
                if (feminine && number == 1) "première" else word
            } else {
                match.value
            }
        }
    }

    // -- Time --

    private val TIME_REGEX = Regex("""(\d{1,2})[hH](\d{2})?\b""")

    private fun expandTime(text: String): String {
        return TIME_REGEX.replace(text) { match ->
            val hours = match.groupValues[1]
            val minutes = match.groupValues[2]
            if (minutes.isNotEmpty() && minutes != "00") {
                "$hours heures $minutes"
            } else {
                "$hours heures"
            }
        }
    }

    // -- Currency --

    private val CURRENCY_AFTER_REGEX = Regex("""(\d[\d\s.,]*)(\s?)(€|\$|£)""")
    private val CURRENCY_BEFORE_REGEX = Regex("""(\$|£)\s?(\d[\d\s.,]*)""")

    private val CURRENCY_NAMES = mapOf(
        "€" to "euros",
        "$" to "dollars",
        "£" to "livres sterling",
    )

    private fun expandCurrency(text: String): String {
        var result = CURRENCY_AFTER_REGEX.replace(text) { match ->
            val amount = match.groupValues[1].trim()
            val symbol = match.groupValues[3]
            val name = CURRENCY_NAMES[symbol] ?: symbol
            "$amount $name"
        }
        result = CURRENCY_BEFORE_REGEX.replace(result) { match ->
            val symbol = match.groupValues[1]
            val amount = match.groupValues[2].trim()
            val name = CURRENCY_NAMES[symbol] ?: symbol
            "$amount $name"
        }
        return result
    }

    // -- Percentage --

    private val PERCENTAGE_REGEX = Regex("""(\d[\d\s.,]*)(\s?)%""")

    private fun expandPercentage(text: String): String {
        return PERCENTAGE_REGEX.replace(text) { match ->
            val number = match.groupValues[1].trim()
            "$number pourcent"
        }
    }

    // -- Units --

    private data class UnitDef(val singular: String, val plural: String)

    private val UNITS = mapOf(
        "km" to UnitDef("kilomètre", "kilomètres"),
        "m" to UnitDef("mètre", "mètres"),
        "cm" to UnitDef("centimètre", "centimètres"),
        "mm" to UnitDef("millimètre", "millimètres"),
        "kg" to UnitDef("kilogramme", "kilogrammes"),
        "g" to UnitDef("gramme", "grammes"),
        "mg" to UnitDef("milligramme", "milligrammes"),
        "L" to UnitDef("litre", "litres"),
        "l" to UnitDef("litre", "litres"),
        "mL" to UnitDef("millilitre", "millilitres"),
        "ml" to UnitDef("millilitre", "millilitres"),
        "cl" to UnitDef("centilitre", "centilitres"),
        "dl" to UnitDef("décilitre", "décilitres"),
        "km/h" to UnitDef("kilomètre par heure", "kilomètres par heure"),
        "m/s" to UnitDef("mètre par seconde", "mètres par seconde"),
        "m²" to UnitDef("mètre carré", "mètres carrés"),
        "m³" to UnitDef("mètre cube", "mètres cubes"),
        "km²" to UnitDef("kilomètre carré", "kilomètres carrés"),
        "ha" to UnitDef("hectare", "hectares"),
        "min" to UnitDef("minute", "minutes"),
        "sec" to UnitDef("seconde", "secondes"),
        "ms" to UnitDef("milliseconde", "millisecondes"),
        "Hz" to UnitDef("hertz", "hertz"),
        "kHz" to UnitDef("kilohertz", "kilohertz"),
        "MHz" to UnitDef("mégahertz", "mégahertz"),
        "GHz" to UnitDef("gigahertz", "gigahertz"),
        "Ko" to UnitDef("kilooctet", "kilooctets"),
        "Mo" to UnitDef("mégaoctet", "mégaoctets"),
        "Go" to UnitDef("gigaoctet", "gigaoctets"),
        "To" to UnitDef("téraoctet", "téraoctets"),
        "°C" to UnitDef("degré Celsius", "degrés Celsius"),
        "°F" to UnitDef("degré Fahrenheit", "degrés Fahrenheit"),
    )

    // Sort by key length descending so "km/h" matches before "km"
    private val UNIT_REGEX = Regex(
        """(\d[\d\s.,]*)\s?(""" +
                UNITS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } +
                """)(?:\b|(?=\s|[.,;:!?]|$))"""
    )

    private fun expandUnits(text: String): String {
        return UNIT_REGEX.replace(text) { match ->
            val number = match.groupValues[1].trim()
            val unit = match.groupValues[2]
            val def = UNITS[unit] ?: return@replace match.value
            val isPlural = try {
                number.replace(",", ".").replace(" ", "").toDouble() != 1.0
            } catch (_: NumberFormatException) {
                true
            }
            "$number ${if (isPlural) def.plural else def.singular}"
        }
    }

    // -- Roman numerals --

    private val ROMAN_VALUES = mapOf(
        'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50,
        'C' to 100, 'D' to 500, 'M' to 1000
    )

    private val ROMAN_WORDS = mapOf(
        1 to "un", 2 to "deux", 3 to "trois", 4 to "quatre", 5 to "cinq",
        6 to "six", 7 to "sept", 8 to "huit", 9 to "neuf", 10 to "dix",
        11 to "onze", 12 to "douze", 13 to "treize", 14 to "quatorze",
        15 to "quinze", 16 to "seize", 17 to "dix-sept", 18 to "dix-huit",
        19 to "dix-neuf", 20 to "vingt", 21 to "vingt-et-un",
    )

    // Matches roman numerals preceded by a name or a contextual word
    private val ROMAN_CONTEXT_REGEX = Regex(
        """(?<=\b(?:[A-ZÀ-Ý][a-zà-ÿ]+|siècle|chapitre|tome|acte|livre|partie|épisode|volume)\s)(I{1,3}|IV|VI{0,3}|IX|XI{0,3}|XIV|XV|XVI{0,3}|XIX|XX|XXI)(?:e|ème)?(?=\b|\s|[.,;:!?]|$)"""
    )

    // Matches "XXe siècle" or "XXIe siècle" patterns
    private val ROMAN_SIECLE_REGEX = Regex(
        """(I{1,3}|IV|VI{0,3}|IX|XI{0,3}|XIV|XV|XVI{0,3}|XIX|XX|XXI)[eè](?:me)?\s+siècle"""
    )

    private fun parseRoman(roman: String): Int? {
        if (roman.isEmpty()) return null
        var total = 0
        var prev = 0
        for (ch in roman.reversed()) {
            val value = ROMAN_VALUES[ch] ?: return null
            if (value < prev) total -= value else total += value
            prev = value
        }
        return if (total in 1..21) total else null
    }

    private fun romanToWord(roman: String): String? {
        val value = parseRoman(roman) ?: return null
        return ROMAN_WORDS[value]
    }

    private fun expandRomanNumerals(text: String): String {
        var result = ROMAN_SIECLE_REGEX.replace(text) { match ->
            val roman = match.value.substringBefore('e').substringBefore('è')
            val word = romanToWord(roman)
            if (word != null) "${word}ième siècle" else match.value
        }
        result = ROMAN_CONTEXT_REGEX.replace(result) { match ->
            romanToWord(match.value.trimEnd('e', 'è')) ?: match.value
        }
        return result
    }
}
