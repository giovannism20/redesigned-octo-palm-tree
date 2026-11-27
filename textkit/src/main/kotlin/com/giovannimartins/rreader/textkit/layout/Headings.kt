package com.giovannimartins.rreader.textkit.layout

/**
 * Recognizes chapter headings by their wording: a line of at most [MAX_LENGTH] characters that starts with a capital
 * letter and is either
 * - a section word and its number, optionally followed by a title after punctuation or a capitalized word:
 *   `CAPÍTULO IV`, `Capítulo 3: A viagem`, `PARTE PRIMEIRA`, `Livro II — O mar`, `Canto I`, `Ato 2`, `Chapter 1`;
 * - an ordinal and `parte` or `livro`: `PRIMEIRA PARTE`, `Segundo livro`;
 * - a section without a number: `Prólogo`, `Epílogo`, `Prefácio`, `Introdução`, `Apresentação`, `Posfácio`,
 *   `Nota do autor`, and their English equivalents.
 *
 * Numbers are digits, Roman numerals and number words (`um`, `primeiro`, `único`, …). Lines of a table of contents,
 * with leader dots or a page number after the heading, are not headings. Whether a matching line really starts a
 * paragraph is decided by the caller.
 */
internal object Headings {
    /** Longest line that can be a heading. */
    const val MAX_LENGTH: Int = 80

    private const val SECTION_WORDS = "capítulo|capitulo|cap\\.|parte|livro|canto|ato|tomo|chapter|part|book"
    private const val ROMAN = "(?=[mdclxvi])m{0,3}(?:cm|cd|d?c{0,3})(?:xc|xl|l?x{0,3})(?:ix|iv|v?i{0,3})"
    private const val ORDINALS =
        "primeir[oa]|segund[oa]|terceir[oa]|quart[oa]|quint[oa]|sext[oa]|s[ée]tim[oa]|oitav[oa]|non[oa]|d[ée]cim[oa]|" +
            "[úu]ltim[oa]"
    private const val CARDINALS =
        "um|uma|dois|duas|tr[êe]s|quatro|cinco|seis|sete|oito|nove|dez|[úu]nic[oa]|" +
            "one|two|three|four|five|six|seven|eight|nine|ten"
    private val NUMBERED =
        Regex(
            "(?iu)(?:(?:$SECTION_WORDS)\\s+(?:\\d{1,3}|$ROMAN|$ORDINALS|$CARDINALS)|(?:$ORDINALS)\\s+(?:parte|livro))" +
                "(?![\\p{L}\\d])(.*)",
        )
    private val UNNUMBERED =
        Regex(
            "(?iu)(?:pr[óo]logo|ep[íi]logo|pref[áa]cio|introdu[çc][ãa]o|apresenta[çc][ãa]o|posf[áa]cio|pre[âa]mbulo|" +
                "nota d[oa] autora?|nota do editor|prologue|epilogue|preface|introduction|foreword|afterword)[.:]?",
        )
    private val TABLE_OF_CONTENTS_LEADER = Regex("\\.{3}|…|(?:\\. ){2}\\.")
    private const val TITLE_SEPARATORS = ".:—–-"

    /** Whether [text] reads like a chapter heading. */
    fun isNamedHeading(text: String): Boolean =
        text.length <= MAX_LENGTH &&
            text.first().isUpperCase() &&
            !TABLE_OF_CONTENTS_LEADER.containsMatchIn(text) &&
            (UNNUMBERED.matches(text) || NUMBERED.matchEntire(text)?.let { titleFollows(it.groupValues[1]) } == true)

    /** After the number: nothing, a title after punctuation, or a capitalized title. */
    private fun titleFollows(rest: String): Boolean {
        val title = rest.trimStart()
        return title.isEmpty() ||
            title.first() in TITLE_SEPARATORS ||
            (title.length < rest.length && title.first().isUpperCase())
    }
}
