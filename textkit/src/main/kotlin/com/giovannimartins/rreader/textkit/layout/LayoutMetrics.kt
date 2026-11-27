package com.giovannimartins.rreader.textkit.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The text column of one page: where its lines start and end. */
internal class Column(
    val left: Float,
    val right: Float,
) {
    val width: Float get() = right - left
}

/**
 * Measurements of a document's body text that the paragraph and heading rules compare each line against.
 *
 * - [bodyFontSize] is the median font size weighted by characters, so the body text decides it.
 * - [lineSpacing] is the median distance between consecutive baselines of lines of similar size, as a multiple of the
 *   font size.
 * - Each page's [column] edges are the smallest left edge and the largest right edge that another line comes within
 *   two body font sizes of, which ignores the odd line that sticks out. Pages are measured on their own, so mirrored
 *   margins are handled; a page with fewer than [MIN_MEASURED_LINES] lines, such as the end of a chapter, cannot show
 *   its column and borrows the one of the nearest measured page on the same side (same parity), else of the nearest
 *   measured page, else measures what it has.
 * - The text is [justified] when at least [JUSTIFIED_SHARE] of the lines that are not last on their page end within
 *   half a body font size of the column's right edge.
 */
internal class LayoutMetrics private constructor(
    val bodyFontSize: Float,
    val lineSpacing: Float,
    val justified: Boolean,
    private val columns: Map<Int, Column>,
) {
    /** The column of page [pageIndex], which must have lines. */
    fun column(pageIndex: Int): Column = columns.getValue(pageIndex)

    companion object {
        const val JUSTIFIED_SHARE: Float = 0.35f
        const val MIN_MEASURED_LINES: Int = 3
        private const val DEFAULT_LINE_SPACING = 1.2f
        private const val FLUSH_TOLERANCE_EM = 0.5f
        private const val COLUMN_TOLERANCE_EM = 2f
        private const val MIN_TOLERANCE = 1f
        private const val SIMILAR_SIZE_RATIO = 0.85f

        fun measure(pages: List<TextPage>): LayoutMetrics {
            val bodyFontSize = bodyFontSize(pages.flatMap(TextPage::lines))
            val columnTolerance = max(bodyFontSize * COLUMN_TOLERANCE_EM, MIN_TOLERANCE)
            val measured =
                pages
                    .filter { it.lines.size >= MIN_MEASURED_LINES }
                    .associate { it.index to column(it, columnTolerance) }
            val columns =
                pages.filter { it.lines.isNotEmpty() }.associate { page ->
                    page.index to
                        (measured[page.index] ?: borrowedColumn(page, measured) ?: column(page, columnTolerance))
                }
            return LayoutMetrics(
                bodyFontSize = bodyFontSize,
                lineSpacing = lineSpacing(pages),
                justified = isJustified(pages, columns, max(bodyFontSize * FLUSH_TOLERANCE_EM, MIN_TOLERANCE)),
                columns = columns,
            )
        }

        /** The column of the nearest measured page on the same side of the spread, else of the nearest one. */
        private fun borrowedColumn(
            page: TextPage,
            measured: Map<Int, Column>,
        ): Column? {
            val sameSide = measured.keys.filter { (it - page.index) % 2 == 0 }.minByOrNull { abs(it - page.index) }
            return (sameSide ?: measured.keys.minByOrNull { abs(it - page.index) })?.let(measured::getValue)
        }

        private fun bodyFontSize(lines: List<TextLine>): Float {
            val sized = lines.filter { it.box.fontSize > 0f }.sortedBy { it.box.fontSize }
            val half = sized.sumOf { it.text.length } / 2
            var seen = 0
            return sized
                .firstOrNull { line ->
                    seen += line.text.length
                    seen > half
                }?.box
                ?.fontSize ?: 0f
        }

        private fun lineSpacing(pages: List<TextPage>): Float {
            val ratios =
                pages
                    .flatMap { page -> page.lines.zipWithNext().mapNotNull { (above, below) -> spacing(above, below) } }
                    .sorted()
            return if (ratios.isEmpty()) DEFAULT_LINE_SPACING else ratios[ratios.size / 2]
        }

        private fun spacing(
            above: TextLine,
            below: TextLine,
        ): Float? {
            val size = max(above.box.fontSize, below.box.fontSize)
            val distance = below.box.bottom - above.box.bottom
            val similar = size > 0f && min(above.box.fontSize, below.box.fontSize) >= size * SIMILAR_SIZE_RATIO
            return if (similar && distance > 0f) distance / size else null
        }

        private fun column(
            page: TextPage,
            tolerance: Float,
        ): Column {
            val lefts = page.lines.map { it.box.left }.sorted()
            val rights = page.lines.map { it.box.right }.sortedDescending()
            return Column(
                left = lefts.firstShared(tolerance) ?: lefts.first(),
                right = rights.firstShared(tolerance) ?: rights.first(),
            )
        }

        /** The first value of this sorted list that the next value lies within [tolerance] of. */
        private fun List<Float>.firstShared(tolerance: Float): Float? =
            zipWithNext().firstOrNull { (value, next) -> abs(value - next) <= tolerance }?.first

        private fun isJustified(
            pages: List<TextPage>,
            columns: Map<Int, Column>,
            tolerance: Float,
        ): Boolean {
            val inner = pages.flatMap { page -> page.lines.dropLast(1) }
            val flush = inner.count { line -> columns.getValue(line.pageIndex).right - line.box.right <= tolerance }
            return inner.isNotEmpty() && flush >= inner.size * JUSTIFIED_SHARE
        }
    }
}
