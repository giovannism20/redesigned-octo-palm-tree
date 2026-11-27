package com.giovannimartins.rreader.textkit.layout

/** Recognition of Roman numerals as books print them in page numbers and headings. */
internal object RomanNumerals {
    /** Only the canonical subtractive form: `IV`, never `IIII` or `IL`. */
    private val CANONICAL = Regex("(?=[MDCLXVI])M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")

    /** Whether [text] is a canonical Roman numeral written entirely in upper case or entirely in lower case. */
    fun isCanonical(text: String): Boolean =
        (text == text.uppercase() || text == text.lowercase()) && CANONICAL.matches(text.uppercase())
}
