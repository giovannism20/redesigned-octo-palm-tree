package com.giovannimartins.rreader.textkit.layout

/**
 * Removes what a book prints in its margins rather than its text: running headers and footers, and page numbers.
 *
 * Only lines that lie entirely in a margin band, the top or bottom [MARGIN_BAND] of the page height, are considered.
 * Such a line is removed when:
 * - it is a page number on its own: Arabic or Roman, optionally after "página"/"pág."/"p."/"page" and between dashes,
 *   dots or brackets, as in `12`, `xii` or `— 12 —`;
 * - the same text (letter case aside) appears in the same band of a page at most [MAX_REPEAT_DISTANCE] pages away, as
 *   a book or chapter title repeated on every page or every other page does;
 * - it differs from such a nearby line only in its digits, and one of its numbers is the page number, as in
 *   `12 DOM CASMURRO`. A number counts as the page number when it stands at the same distance from the page's index as
 *   the numbers in the margins of at least [MIN_NUMBERED_PAGES] pages do.
 *
 * Requiring nearby repeats keeps chapter headings printed at the top of their first page, such as `Capítulo 1` and
 * `Capítulo 2`, and a body line that happens to start two distant pages with the same words.
 */
internal object RunningLines {
    /** Share of the page height, at the top and at the bottom, that counts as margin. */
    const val MARGIN_BAND: Float = 0.1f

    /** Pages whose margin numbers must agree before that agreement identifies printed page numbers. */
    const val MIN_NUMBERED_PAGES: Int = 3

    /** Farthest page, before or after, where the repeat of a running line is looked for. */
    const val MAX_REPEAT_DISTANCE: Int = 2

    private const val MAX_PAGE_NUMBER_DIGITS = 6
    private const val PAGE_DECORATION = "[\\p{Pd}\\s.·•|()\\[\\]]*"
    private val PAGE_NUMBER =
        Regex(
            "^$PAGE_DECORATION(?:(?iu:p[áa]gina|p[áa]g\\.?|p\\.|page)\\s*)?" +
                "(\\d{1,4}|[ivxlcdm]{1,9}|[IVXLCDM]{1,9})$PAGE_DECORATION$",
        )
    private val DIGITS = Regex("\\d+")

    /** [pages] without their running lines. */
    fun remove(pages: List<TextPage>): List<TextPage> {
        val margins = pages.flatMap { page -> page.lines.mapNotNull { line -> marginLine(page, line) } }
        val running = HashSet<TextLine>()
        margins.filter { isPageNumber(it.line.text) }.mapTo(running) { it.line }
        repeatedText(margins).mapTo(running) { it.line }
        numberedRepeats(margins.filterNot { it.line in running }, pageNumberOffsets(margins)).mapTo(running) { it.line }
        return pages.map { page -> page.withLines(page.lines.filterNot(running::contains)) }
    }

    /** Whether [text] is a page number alone, such as `12`, `- 12 -`, `[xii]` or `Página 7`. */
    fun isPageNumber(text: String): Boolean {
        val number = PAGE_NUMBER.matchEntire(text)?.groupValues?.get(1) ?: return false
        return number.first().isDigit() || RomanNumerals.isCanonical(number)
    }

    private fun marginLine(
        page: TextPage,
        line: TextLine,
    ): MarginLine? {
        val band =
            when {
                page.height <= 0f -> null
                line.box.bottom <= page.height * MARGIN_BAND -> Band.TOP
                line.box.top >= page.height * (1f - MARGIN_BAND) -> Band.BOTTOM
                else -> null
            }
        return band?.let { MarginLine(line, it, line.text.lowercase()) }
    }

    private fun repeatedText(margins: List<MarginLine>): List<MarginLine> =
        margins.groupBy { it.band to it.key }.values.flatMap { group -> group.repeatedNearby() }

    /** Lines that repeat once digits are ignored and carry a page number. */
    private fun numberedRepeats(
        margins: List<MarginLine>,
        offsets: Set<Long>,
    ): List<MarginLine> =
        margins
            .filter { DIGITS.containsMatchIn(it.key) }
            .groupBy { it.band to it.key.replace(DIGITS, "#") }
            .values
            .flatMap { group -> group.repeatedNearby() }
            .filter { margin -> margin.numbers().any { number -> number - margin.line.pageIndex in offsets } }

    /**
     * Distances between a printed number and the page index that the margins of at least [MIN_NUMBERED_PAGES] pages
     * share: the offsets of the page numbering (several when the numbering restarts).
     */
    private fun pageNumberOffsets(margins: List<MarginLine>): Set<Long> {
        val pagesByOffset = HashMap<Long, MutableSet<Int>>()
        for (margin in margins) {
            for (number in margin.numbers()) {
                pagesByOffset.getOrPut(number - margin.line.pageIndex) { HashSet() } += margin.line.pageIndex
            }
        }
        return pagesByOffset.filterValues { it.size >= MIN_NUMBERED_PAGES }.keys
    }

    /** The lines of this group whose page has another line of the group at most [MAX_REPEAT_DISTANCE] pages away. */
    private fun List<MarginLine>.repeatedNearby(): List<MarginLine> {
        val pages = mapTo(HashSet()) { it.line.pageIndex }
        return filter { margin ->
            (1..MAX_REPEAT_DISTANCE).any { distance ->
                margin.line.pageIndex + distance in pages || margin.line.pageIndex - distance in pages
            }
        }
    }

    private fun MarginLine.numbers(): List<Long> =
        DIGITS
            .findAll(key)
            .map { it.value }
            .filter { it.length <= MAX_PAGE_NUMBER_DIGITS }
            .map(String::toLong)
            .toList()

    private enum class Band { TOP, BOTTOM }

    private class MarginLine(
        val line: TextLine,
        val band: Band,
        /** The text in lower case, so that `DOM CASMURRO` and `Dom Casmurro` match. */
        val key: String,
    )
}
