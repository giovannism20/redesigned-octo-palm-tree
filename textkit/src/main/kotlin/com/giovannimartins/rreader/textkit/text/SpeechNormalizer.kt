package com.giovannimartins.rreader.textkit.text

/**
 * Rewrites text just before it is spoken, so voices read it naturally. The stored chapter text never changes
 * (see ADR 0006); these rewrites exist only in the string handed to the speech engine.
 *
 * The rewrites are deliberately conservative: when a rule is unsure, the text is left as it is.
 * - Roman numerals become Arabic numbers after a section keyword, in any case: `capítulo`, `parte`, `livro`,
 *   `volume`, `tomo`, `canto`, `ato`, `cena`, `seção`, `século` (`século XIX` → `século 19`). Elsewhere in body
 *   text they are never touched, so `D. Pedro II`, `Luís XIV` and a standalone `I` or `V.` stay as written.
 * - In headings, a whole-heading numeral (`XII`) or a leading one followed by a separator (`XII — O enterro`,
 *   `IV. A volta`) becomes a number too, and ALL-CAPS words longer than three letters are put in sentence case
 *   (`CAPÍTULO PRIMEIRO` → `Capítulo primeiro`). Shorter words, which may be acronyms, are kept.
 * - Numerals are converted only when canonical (`IV`, not `IIII`), at most 399, and not a lone `C`, `D`, `L`
 *   or `M`, which are more often letters (`Seção C`).
 * - Symbols that voices read aloud badly (`*`, `#`, `_`, `~`, `•`, `|`) are removed, keeping words apart;
 *   runs of `!` and `?` collapse to one mark (`?!` when mixed); `...` becomes `…`.
 *
 * The result may be blank, for example for a `* * *` scene break; callers skip blank speech. Instances are
 * stateless and thread-safe.
 */
public class SpeechNormalizer {
    /** The speakable form of [text]; pass [isHeading] for chapter and section titles. */
    public fun toSpeech(
        text: String,
        isHeading: Boolean = false,
    ): String {
        val cleaned = ELLIPSIS.replace(collapseRepeatedMarks(removeUnspeakableSymbols(text)), "…")
        val numbered = numberKeywordNumerals(cleaned)
        return if (isHeading) HeadingCase.toSentenceCase(numberLeadingNumeral(numbered)) else numbered
    }

    private fun removeUnspeakableSymbols(text: String): String =
        UNSPEAKABLE.replace(text) { match ->
            val before = text.getOrNull(match.range.first - 1)
            val after = text.getOrNull(match.range.last + 1)
            val separatesWords = before?.let(::canPrecedeSpace) == true && after?.let(::canFollowSpace) == true
            if (separatesWords) " " else ""
        }

    private fun canPrecedeSpace(char: Char): Boolean = !char.isWhitespace() && char !in NO_SPACE_AFTER

    private fun canFollowSpace(char: Char): Boolean = !char.isWhitespace() && char !in NO_SPACE_BEFORE

    private fun collapseRepeatedMarks(text: String): String =
        REPEATED_MARKS.replace(text) { match ->
            val asks = match.value.any { it in QUESTION_MARKS }
            val exclaims = match.value.any { it in EXCLAMATION_MARKS }
            when {
                asks && exclaims -> "?!"
                asks -> "?"
                else -> "!"
            }
        }

    private fun numberKeywordNumerals(text: String): String =
        KEYWORD_NUMERAL.replace(text) { match ->
            val (keyword, space, numeral) = match.destructured
            val number = if (numeral.all(Char::isUpperCase)) RomanNumerals.sectionNumber(numeral) else null
            if (number == null) match.value else "$keyword$space$number"
        }

    private fun numberLeadingNumeral(heading: String): String =
        LEADING_NUMERAL.matchEntire(heading)?.let { match ->
            val (indent, numeral, rest) = match.destructured
            RomanNumerals.sectionNumber(numeral)?.let { number -> "$indent$number$rest" }
        } ?: heading

    private companion object {
        /** Spaces that may surround a symbol or separate a keyword from its numeral, without line breaks. */
        const val SPACE = """[\t   ]"""

        val UNSPEAKABLE = Regex("""$SPACE*[*#_~•|][*#_~•|\t   ]*""")
        val REPEATED_MARKS = Regex("[!?‼⁇⁈⁉]{2,}|[‼⁇⁈⁉]")
        val ELLIPSIS = Regex("""\.{3,}""")

        /** Keyword, spaces, numeral. Case-insensitive, so the numeral's case is checked on each match. */
        val KEYWORD_NUMERAL =
            Regex(
                """(?<![\p{L}\p{N}])(cap[íi]tulo|parte|livro|volume|tomo|canto|ato|cena|sec?[çc][ãa]o|s[ée]culo)""" +
                    """($SPACE+)([ivxlcdm]+)(?![\p{L}\p{N}])""",
                RegexOption.IGNORE_CASE,
            )

        /**
         * Indent, numeral, rest. The rest is empty or a lone `.`/`:` (the numeral is the whole heading), or a
         * separator and a title: `. O enterro`, `: O enterro`, ` — O enterro`, ` - O enterro`. A separator
         * followed by an initial (`I. M. Fulano`) is a name, not a number.
         */
        val LEADING_NUMERAL =
            Regex(
                """($SPACE*)([IVXLCDM]+)($SPACE*[.:]?$SPACE*|$SPACE*[.:]$SPACE+(?!\p{Lu}\.).*""" +
                    """|$SPACE*[—–]$SPACE*\S.*|$SPACE+-$SPACE+\S.*)""",
                RegexOption.DOT_MATCHES_ALL,
            )

        const val QUESTION_MARKS = "?⁇⁈⁉"
        const val EXCLAMATION_MARKS = "!‼⁈⁉"

        /** A removed symbol leaves no space after these openers... */
        const val NO_SPACE_AFTER = "([{«“‘\"'"

        /** ...nor before these closers and punctuation marks. */
        const val NO_SPACE_BEFORE = ".,;:!?…)]}»”’\"'"
    }
}
