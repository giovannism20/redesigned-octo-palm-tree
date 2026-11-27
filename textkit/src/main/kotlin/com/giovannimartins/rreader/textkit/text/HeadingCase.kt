package com.giovannimartins.rreader.textkit.text

/**
 * Sentence case for ALL-CAPS headings, which some voices spell out or stress: `CAPÍTULO PRIMEIRO` becomes
 * `Capítulo primeiro`. Only shouted words longer than three letters change; shorter ones may be acronyms
 * (`ONU`, `USP`), and valid Roman numerals (`VIII`) keep their capitals so voices still read them as numbers.
 */
internal object HeadingCase {
    private const val MIN_SHOUTED_LENGTH = 4

    /** Letters with their combining marks, so decomposed accents stay inside the word. */
    private val WORD = Regex("""[\p{L}\p{M}]+""")

    fun toSentenceCase(heading: String): String {
        val firstWordStart = WORD.find(heading)?.range?.first
        return WORD.replace(heading) { match ->
            val word = match.value
            when {
                !isShouted(word) -> word
                match.range.first == firstWordStart -> word.lowercase().replaceFirstChar(Char::titlecase)
                else -> word.lowercase()
            }
        }
    }

    private fun isShouted(word: String): Boolean =
        word.count(Char::isLetter) >= MIN_SHOUTED_LENGTH &&
            word.none(Char::isLowerCase) &&
            RomanNumerals.parse(word) == null
}
