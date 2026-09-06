package io.github.kvnpetit.betterfrenchtts.preprocessing

import java.math.BigDecimal
import java.time.LocalDate

/** Regional number conventions. Swiss French defaults to quatre-vingts (not huitante). */
enum class FrenchRegion { FRANCE, BELGIUM, SWITZERLAND, CANADA }

/** Explicit, deterministic readings for ambiguous data. No engine-specific markup. */
object FrenchFormats {
    private val small = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize")
    private val tens = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante", "septante", "", "nonante")

    /** Integers up to 999,999,999,999; larger values are rejected rather than approximated. */
    fun cardinal(value: Long, region: FrenchRegion = FrenchRegion.FRANCE): String {
        require(value in -999_999_999_999L..999_999_999_999L) { "Number outside supported range" }
        if (value < 0) return "moins " + cardinal(-value, region)
        if (value < 17) return small[value.toInt()]
        if (value < 20) return "dix-" + small[(value - 10).toInt()]
        if (value < 100) {
            val n = value.toInt()
            val regional = region == FrenchRegion.BELGIUM || region == FrenchRegion.SWITZERLAND
            if (n in 70..79 && !regional) return "soixante" + (if (n == 71) " et " else "-") + cardinal(value - 60, region)
            if (n in 80..89 || n >= 90 && !regional) {
                return if (n == 80) "quatre-vingts" else "quatre-vingt-" + cardinal(value - 80, region)
            }
            return tens[n / 10] + when (n % 10) {
                0 -> ""
                1 -> " et un"
                else -> "-" + small[n % 10]
            }
        }
        if (value < 1000) {
            val count = value / 100
            val remainder = value % 100
            return (if (count == 1L) "cent" else cardinal(count, region) + " cent" + if (remainder == 0L) "s" else "") +
                if (remainder == 0L) "" else " " + cardinal(remainder, region)
        }
        val scale = when {
            value >= 1_000_000_000 -> 1_000_000_000L
            value >= 1_000_000 -> 1_000_000L
            else -> 1000L
        }
        val count = value / scale
        val remainder = value % scale
        val head = if (scale == 1000L) {
            if (count == 1L) "mille" else cardinal(count, region).let {
                if (it.endsWith("cents") || it.endsWith("vingts")) it.dropLast(1) else it
            } + " mille"
        } else cardinal(count, region) + (if (scale == 1_000_000L) " million" else " milliard") + if (count > 1) "s" else ""
        return head + if (remainder == 0L) "" else " " + cardinal(remainder, region)
    }

    /** French decimal comma; decimal digits are read individually to preserve trailing zeros. */
    fun number(value: String, region: FrenchRegion = FrenchRegion.FRANCE): String {
        val normalized = value.replace(Regex("[ \u00a0\u202f]"), "").replace(',', '.')
        require(normalized.matches(Regex("[+-]?\\d+(?:\\.\\d+)?"))) { "Invalid decimal number" }
        val parts = normalized.removePrefix("+").split('.')
        val integer = parts[0].toLongOrNull() ?: throw IllegalArgumentException("Number outside supported range")
        val whole = if (integer == 0L && parts[0].startsWith('-')) "moins zéro" else cardinal(integer, region)
        return whole + if (parts.size == 1) "" else " virgule " + parts[1].map { small[it.digitToInt()] }.joinToString(" ")
    }

    fun ordinal(value: Long, feminine: Boolean = false, region: FrenchRegion = FrenchRegion.FRANCE): String {
        require(value > 0) { "Ordinal must be positive" }
        if (value == 1L) return if (feminine) "première" else "premier"
        var stem = cardinal(value, region).replace(" et ", "-et-").replace(' ', '-')
        if (stem.endsWith("cents") || stem.endsWith("vingts") || stem.endsWith("millions") || stem.endsWith("milliards")) stem = stem.dropLast(1)
        stem = when {
            stem.endsWith("cinq") -> stem + "u"
            stem.endsWith("neuf") -> stem.dropLast(1) + "v"
            stem.endsWith("e") -> stem.dropLast(1)
            else -> stem
        }
        return stem + "ième"
    }

    fun money(amount: String, currency: String = "EUR", region: FrenchRegion = FrenchRegion.FRANCE): String {
        val normalized = amount.replace(Regex("[ \u00a0\u202f]"), "").replace(',', '.')
        require(normalized.matches(Regex("[+-]?\\d+(?:\\.\\d{1,2})?"))) { "Use a decimal amount with at most two fractional digits" }
        val value = BigDecimal(normalized)
        require(value.scale() <= 2) { "Money supports at most two fractional digits" }
        val names = when (currency.uppercase(java.util.Locale.ROOT)) {
            "EUR", "€" -> "euro" to "centime"
            "USD", "CAD", "$" -> "dollar" to "cent"
            "GBP", "£" -> "livre sterling" to "penny"
            else -> throw IllegalArgumentException("Unsupported currency")
        }
        val absolute = value.abs()
        require(absolute <= BigDecimal("999999999999.99")) { "Amount outside supported range" }
        // The bound above makes conversion exact, including on Android API 26.
        val whole = absolute.toBigInteger().toLong()
        val cents = absolute.remainder(BigDecimal.ONE).movePointRight(2).intValueExact()
        fun plural(name: String, n: Long) = when {
            n <= 1 -> name
            name == "livre sterling" -> "livres sterling"
            name == "penny" -> "pence"
            else -> name + "s"
        }
        val wholeWords = if (names.first == "livre sterling" && whole == 1L) "une" else cardinal(whole, region)
        return (if (value.signum() < 0) "moins " else "") + wholeWords + " " + plural(names.first, whole) +
            if (cents == 0) "" else " et " + cardinal(cents.toLong(), region) + " " + plural(names.second, cents.toLong())
    }

    fun date(value: String, format: String = "dmy", region: FrenchRegion = FrenchRegion.FRANCE): String {
        val pieces = value.split(Regex("[/.-]"))
        require(pieces.size == 3 && pieces.all { it.matches(Regex("\\d+")) }) { "Expected a complete numeric date" }
        val order = when (format) { "dmy" -> listOf(2, 1, 0); "ymd" -> listOf(0, 1, 2); "mdy" -> listOf(2, 0, 1); else -> throw IllegalArgumentException("Use dmy, mdy or ymd") }
        require(pieces[order[0]].length == 4) { "Use a four-digit year" }
        val date = try { LocalDate.of(pieces[order[0]].toInt(), pieces[order[1]].toInt(), pieces[order[2]].toInt()) }
        catch (error: java.time.DateTimeException) { throw IllegalArgumentException("Invalid calendar date", error) }
        val months = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
        return (if (date.dayOfMonth == 1) "premier" else cardinal(date.dayOfMonth.toLong(), region)) + " " + months[date.monthValue - 1] + " " + cardinal(date.year.toLong(), region)
    }

    /** French domestic numbers are grouped by pairs; international/other numbers are spelled. */
    fun telephone(value: String, region: FrenchRegion = FrenchRegion.FRANCE): String {
        require(value.matches(Regex("\\+?[0-9 () .-]+"))) { "Invalid telephone number" }
        val digits = value.filter { it in '0'..'9' }
        require(digits.length in 3..15) { "Telephone number must contain 3 to 15 digits" }
        if (digits.length == 10 && digits.startsWith('0') && !value.startsWith('+') && region == FrenchRegion.FRANCE) {
            return digits.chunked(2).joinToString(", ") { if (it.startsWith('0')) "zéro " + small[it[1].digitToInt()] else cardinal(it.toLong(), region) }
        }
        return (if (value.startsWith('+')) "plus " else "") + digits.map { small[it.digitToInt()] }.joinToString(" ")
    }

    fun duration(totalSeconds: Long, region: FrenchRegion = FrenchRegion.FRANCE): String {
        require(totalSeconds >= 0) { "Duration cannot be negative" }
        val values = listOf(totalSeconds / 3600 to "heure", totalSeconds / 60 % 60 to "minute", totalSeconds % 60 to "seconde")
        return values.filter { it.first != 0L }.joinToString(" et ") { (n, unit) ->
            (if (n == 1L) "une" else cardinal(n, region)) + " " + unit + if (n > 1) "s" else ""
        }.ifEmpty { "zéro seconde" }
    }
}
