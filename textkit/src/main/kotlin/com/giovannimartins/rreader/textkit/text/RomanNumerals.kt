package com.giovannimartins.rreader.textkit.text

/** Strict parsing of uppercase Roman numerals. */
internal object RomanNumerals {
    /** Largest value read as a chapter, part or century number; bigger numerals are left alone. */
    const val MAX_SECTION_NUMBER: Int = 399

    /** Only the canonical subtractive form: `IV`, never `IIII` or `IL`. */
    private val CANONICAL = Regex("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")
    private val VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)

    /** Single-letter numerals plausible as section numbers; a lone `C`, `D`, `L` or `M` is more often a letter. */
    private const val SINGLE_LETTER_SECTIONS = "IVX"

    /** The value of a canonical uppercase [numeral], or null when it is not one. */
    fun parse(numeral: String): Int? =
        if (numeral.isNotEmpty() && CANONICAL.matches(numeral)) valueOf(numeral) else null

    /**
     * The value of [numeral] when it is plausible as the number of a chapter, part or century: canonical,
     * at most [MAX_SECTION_NUMBER], and not a lone `C`, `D`, `L` or `M` (which are more often letters, as in
     * `Seção C`).
     */
    fun sectionNumber(numeral: String): Int? =
        parse(numeral)?.takeIf { value ->
            value <= MAX_SECTION_NUMBER && (numeral.length > 1 || numeral[0] in SINGLE_LETTER_SECTIONS)
        }

    private fun valueOf(numeral: String): Int =
        numeral.indices.sumOf { index ->
            val value = VALUES.getValue(numeral[index])
            val next = numeral.getOrNull(index + 1)?.let(VALUES::getValue) ?: 0
            if (value < next) -value else value
        }
}
