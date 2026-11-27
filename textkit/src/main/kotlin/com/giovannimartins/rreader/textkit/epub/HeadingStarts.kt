package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.layout.Headings
import com.giovannimartins.rreader.textkit.text.RomanNumerals

/**
 * Finds the chapters inside sections too long to be one chapter, for books whose table of contents is coarse (it
 * lists only the title page, the parts or the license) or missing.
 *
 * A section longer than [ChapterAssembler.LONG_SECTION_LENGTH] characters is split at its chapter headings
 * ([ChapterHeadings]) and chapter divisions ([ContentDocument.chapterDivisions]) when it holds at least two of them;
 * otherwise at its `h1`–`h3` headings, again when it holds at least two. Each new chapter is titled by its heading.
 */
internal object HeadingStarts {
    private const val MIN_HEADINGS = 2

    /** The chapter starts found inside the sections that [starts] delimit, besides [starts] themselves. */
    fun inLongSections(
        starts: List<ChapterStart>,
        text: SpineText,
        budget: ParseBudget = ParseBudget.unlimited(),
    ): List<ChapterStart> {
        val bounds = (listOf(0) + starts.map { it.paragraph } + text.size).distinct().sorted()
        return bounds
            .zipWithNext()
            .filter { (from, to) -> text.length(from, to) > ChapterAssembler.LONG_SECTION_LENGTH }
            .flatMap { (from, to) ->
                budget.check()
                split(from, to, text)
            }
    }

    private fun split(
        from: Int,
        to: Int,
        text: SpineText,
    ): List<ChapterStart> {
        val headings = text.headingsIn(from, to)
        val titles = headings.associate { it.firstParagraph to it.text }
        val chapterHeadings = headings.filter { ChapterHeadings.isChapterHeading(it.text) }.map { it.firstParagraph }
        val divisions = text.chapterDivisionsIn(from, to)
        val tiers =
            sequenceOf(
                (chapterHeadings + divisions).distinct().sorted(),
                headings.filter { it.isTitleLevel }.map { it.firstParagraph },
            )
        val chosen = tiers.firstOrNull { it.size >= MIN_HEADINGS }.orEmpty()
        return chosen.filter { it > from }.map { ChapterStart(it, titles[it]) }
    }
}

/**
 * Recognizes headings that open a chapter by their wording: the section words, ordinals and named sections that
 * [Headings] knows (`CAPÍTULO IV`, `CAPITULO PRIMEIRO`, `Parte II`, `Prólogo`), or a chapter number on its own or
 * before a title (`I`, `XII.`, `12`, `IV — A partida`, `I A LIÇÃO DE VIOLÃO`). Roman numerals are upper case and
 * canonical, up to [RomanNumerals.MAX_SECTION_NUMBER]; a lone `C`, `D`, `L` or `M` is read as a letter, as in
 * `D. Gloria`.
 */
internal object ChapterHeadings {
    private val NUMBERED = Regex("([IVXLCDM]+|\\d{1,3})(?:[\\s.:)–—-].*)?")

    /** Whether [text], a heading's text on one line, reads like the start of a chapter. */
    fun isChapterHeading(text: String): Boolean =
        text.isNotEmpty() && text.length <= Headings.MAX_LENGTH && (Headings.isNamedHeading(text) || isNumbered(text))

    private fun isNumbered(text: String): Boolean {
        val number = NUMBERED.matchEntire(text)?.groupValues?.get(1) ?: return false
        return number.first().isDigit() || RomanNumerals.sectionNumber(number) != null
    }
}
