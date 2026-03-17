package com.github.kvnpetit.betterfrenchtts.spelling

/**
 * Maps characters to their spoken French name for spell-out.
 *
 * Covers: French accents, AZERTY keyboard, typographic punctuation,
 * currencies, math symbols, arrows, Greek letters, and more (200+ chars).
 *
 * Characters not in the map are handled by fallback logic in [resolve].
 */
object FrenchCharMap {

    /**
     * Returns the French spoken name for [char], or null if TTS can handle it natively (a-z, 0-9).
     *
     * Fallback chain:
     * 1. Direct mapping (accents, symbols, punctuation)
     * 2. Uppercase A-Z -> "X majuscule"
     * 3. Lowercase a-z / digits 0-9 -> null (TTS say-as handles these)
     * 4. Unmapped uppercase accent -> decompose from lowercase + "majuscule"
     * 5. Unknown Unicode -> "caractere unicode [code]"
     */
    fun resolve(char: Char): String? {
        CHAR_NAMES[char]?.let { return it }

        if (char in 'A'..'Z') return "${char} majuscule"

        if (char in 'a'..'z' || char in '0'..'9') return null

        val lower = char.lowercaseChar()
        CHAR_NAMES[lower]?.let { return "$it majuscule" }

        return "caractère unicode ${char.code}"
    }

    private val CHAR_NAMES = buildMap<Char, String> {
        // ============================
        // FRENCH ACCENTED LOWERCASE
        // ============================
        put('é', "é accent aigu")
        put('è', "è accent grave")
        put('ê', "e accent circonflexe")
        put('ë', "e tréma")
        put('à', "a accent grave")
        put('â', "a accent circonflexe")
        put('ä', "a tréma")
        put('ù', "u accent grave")
        put('û', "u accent circonflexe")
        put('ü', "u tréma")
        put('ô', "o accent circonflexe")
        put('ö', "o tréma")
        put('î', "i accent circonflexe")
        put('ï', "i tréma")
        put('ç', "c cédille")
        put('ÿ', "y tréma")
        put('ñ', "n tilde")

        // ============================
        // FRENCH ACCENTED UPPERCASE
        // ============================
        put('É', "É majuscule accent aigu")
        put('È', "È majuscule accent grave")
        put('Ê', "E majuscule accent circonflexe")
        put('Ë', "E majuscule tréma")
        put('À', "A majuscule accent grave")
        put('Â', "A majuscule accent circonflexe")
        put('Ä', "A majuscule tréma")
        put('Ù', "U majuscule accent grave")
        put('Û', "U majuscule accent circonflexe")
        put('Ü', "U majuscule tréma")
        put('Ô', "O majuscule accent circonflexe")
        put('Ö', "O majuscule tréma")
        put('Î', "I majuscule accent circonflexe")
        put('Ï', "I majuscule tréma")
        put('Ç', "C majuscule cédille")
        put('Ÿ', "Y majuscule tréma")
        put('Ñ', "N majuscule tilde")

        // ============================
        // LIGATURES
        // ============================
        put('œ', "o e liés")
        put('Œ', "O E liés majuscule")
        put('æ', "a e liés")
        put('Æ', "A E liés majuscule")

        // ============================
        // PUNCTUATION
        // ============================
        put('.', "point")
        put(',', "virgule")
        put(';', "point-virgule")
        put(':', "deux-points")
        put('!', "point d'exclamation")
        put('?', "point d'interrogation")
        put('\u2026', "points de suspension")       // …
        put('\u00B7', "point médian")                // ·
        put('\'', "apostrophe")
        put('\u2019', "apostrophe typographique")    // '
        put('\u2018', "apostrophe ouvrante")         // '
        put('"', "guillemet droit")
        put('\u201C', "guillemet ouvrant anglais")   // "
        put('\u201D', "guillemet fermant anglais")   // "
        put('\u00AB', "guillemet ouvrant français")  // «
        put('\u00BB', "guillemet fermant français")  // »
        put('-', "tiret")
        put('\u2013', "tiret demi-cadratin")         // –
        put('\u2014', "tiret cadratin")              // —
        put('\u2015', "tiret long")                  // ―
        put('\u00AD', "tiret de césure")             // soft hyphen

        // ============================
        // BRACKETS
        // ============================
        put('(', "parenthèse ouvrante")
        put(')', "parenthèse fermante")
        put('[', "crochet ouvrant")
        put(']', "crochet fermant")
        put('{', "accolade ouvrante")
        put('}', "accolade fermante")
        put('<', "chevron ouvrant")
        put('>', "chevron fermant")

        // ============================
        // AZERTY KEYBOARD — DIRECT & SHIFT
        // ============================
        put('&', "esperluette")
        put('#', "dièse")
        put('@', "arobase")
        put('_', "tiret bas")
        put('\\', "antislash")
        put('/', "slash")
        put('|', "barre verticale")
        put('~', "tilde")
        put('^', "accent circonflexe")
        put('`', "accent grave")
        put('\u00B0', "degré")                       // °
        put('+', "plus")
        put('=', "égal")
        put('*', "astérisque")
        put('\u00A7', "paragraphe")                  // §
        put('\u00A8', "tréma")                       // ¨
        put('\u00B5', "mu")                          // µ
        put('\u00B2', "carré")                       // ²
        put('\u00B3', "cube")                        // ³
        put('\u00B9', "exposant un")                 // ¹

        // ============================
        // AZERTY KEYBOARD — ALTGR
        // ============================
        put('\u00A4', "symbole monétaire")           // ¤
        put('\u00AC', "négation")                    // ¬
        put('\u00A6', "barre verticale brisée")      // ¦
        put('\u00A1', "point d'exclamation inversé") // ¡
        put('\u00BF', "point d'interrogation inversé") // ¿

        // ============================
        // CURRENCIES
        // ============================
        put('\u20AC', "euro")                        // €
        put('$', "dollar")
        put('\u00A3', "livre sterling")              // £
        put('\u00A5', "yen")                         // ¥
        put('\u00A2', "centime")                     // ¢
        put('\u20BF', "bitcoin")                     // ₿
        put('\u20B9', "roupie")                      // ₹
        put('\u20BD', "rouble")                      // ₽
        put('\u20A9', "won")                         // ₩
        put('\u20BA', "livre turque")                // ₺
        put('\u20B4', "hryvnia")                     // ₴
        put('\u20AB', "dong")                        // ₫
        put('\u20B1', "peso")                        // ₱
        put('\u20B8', "tenge")                       // ₸
        put('\u20A6', "naira")                       // ₦
        put('\u20B5', "cedi")                        // ₵
        put('\u0E3F', "baht")                        // ฿
        put('\u20A1', "colon")                       // ₡
        put('\u20AD', "kip")                         // ₭
        put('\u20AE', "tugrik")                      // ₮
        put('\u20B2', "guarani")                     // ₲
        put('\u20AA', "shekel")                      // ₪
        put('\u20A3', "franc")                       // ₣
        put('\u20A4', "lire")                        // ₤
        put('\u20BC', "manat")                       // ₼
        put('\u20BE', "lari")                        // ₾

        // ============================
        // MATHEMATICS
        // ============================
        put('\u00D7', "multiplié")                   // ×
        put('\u00F7', "divisé")                      // ÷
        put('\u00B1', "plus ou moins")               // ±
        put('\u2260', "différent")                   // ≠
        put('\u2248', "environ égal")                // ≈
        put('\u2264', "inférieur ou égal")           // ≤
        put('\u2265', "supérieur ou égal")           // ≥
        put('\u221A', "racine carrée")               // √
        put('\u221E', "infini")                      // ∞
        put('\u2211', "somme")                       // ∑
        put('\u220F', "produit")                     // ∏
        put('\u222B', "intégrale")                   // ∫
        put('\u2202', "dérivée partielle")           // ∂
        put('\u2206', "delta")                       // ∆
        put('\u2207', "nabla")                       // ∇
        put('\u2208', "appartient à")                // ∈
        put('\u2209', "n'appartient pas à")          // ∉
        put('\u2282', "inclus dans")                 // ⊂
        put('\u2283', "contient")                    // ⊃
        put('\u222A', "union")                       // ∪
        put('\u2229', "intersection")                // ∩
        put('\u2205', "ensemble vide")               // ∅
        put('\u2200', "pour tout")                   // ∀
        put('\u2203', "il existe")                   // ∃
        put('\u00BC', "un quart")                    // ¼
        put('\u00BD', "un demi")                     // ½
        put('\u00BE', "trois quarts")                // ¾
        put('\u2030', "pour mille")                  // ‰
        put('%', "pourcent")

        // ============================
        // GREEK LETTERS
        // ============================
        put('\u03C0', "pi")                          // π
        put('\u03B1', "alpha")                       // α
        put('\u03B2', "bêta")                        // β
        put('\u03B3', "gamma")                       // γ
        put('\u03B4', "delta")                       // δ
        put('\u03B5', "epsilon")                     // ε
        put('\u03B8', "thêta")                       // θ
        put('\u03BB', "lambda")                      // λ
        put('\u03C3', "sigma")                       // σ
        put('\u03C6', "phi")                         // φ
        put('\u03C9', "oméga")                       // ω
        put('\u03A9', "oméga majuscule")             // Ω

        // ============================
        // ARROWS
        // ============================
        put('\u2192', "flèche droite")               // →
        put('\u2190', "flèche gauche")               // ←
        put('\u2191', "flèche haut")                 // ↑
        put('\u2193', "flèche bas")                  // ↓
        put('\u2194', "flèche gauche-droite")        // ↔
        put('\u2195', "flèche haut-bas")             // ↕
        put('\u21D2', "double flèche droite")        // ⇒
        put('\u21D0', "double flèche gauche")        // ⇐
        put('\u21D4', "double flèche gauche-droite") // ⇔

        // ============================
        // MISCELLANEOUS SYMBOLS
        // ============================
        put('\u00A9', "copyright")                   // ©
        put('\u00AE', "marque déposée")              // ®
        put('\u2122', "trademark")                   // ™
        put('\u2022', "puce")                        // •
        put('\u25E6', "puce creuse")                 // ◦
        put('\u2023', "puce triangulaire")           // ‣
        put('\u00B6', "pied-de-mouche")              // ¶
        put('\u2020', "obèle")                       // †
        put('\u2021', "double obèle")                // ‡
        put('\u2660', "pique")                       // ♠
        put('\u2663', "trèfle")                      // ♣
        put('\u2665', "cœur")                        // ♥
        put('\u2666', "carreau")                     // ♦
        put('\u266A', "note de musique")             // ♪
        put('\u266B', "double croche")               // ♫
        put('\u2713', "coche")                       // ✓
        put('\u2717', "croix")                       // ✗
        put('\u2714', "coche épaisse")               // ✔
        put('\u2718', "croix épaisse")               // ✘
        put('\u2605', "étoile pleine")               // ★
        put('\u2606', "étoile vide")                 // ☆

        // ============================
        // SPECIAL WHITESPACE
        // ============================
        put('\t', "tabulation")
        put('\n', "retour à la ligne")
        put('\u00A0', "espace insécable")
        put('\u200B', "espace sans largeur")
        put('\u202F', "espace fine insécable")
    }
}
