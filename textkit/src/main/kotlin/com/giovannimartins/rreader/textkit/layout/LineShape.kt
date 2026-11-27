package com.giovannimartins.rreader.textkit.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Geometric facts about lines, measured against the document's [LayoutMetrics]. */
internal class LineShape(
    private val metrics: LayoutMetrics,
) {
    /** The gap above [line] is more than [GAP_RATIO] times the usual line spacing; only within a page. */
    fun hasGapBefore(
        previous: TextLine,
        line: TextLine,
    ): Boolean {
        val size = max(previous.box.fontSize, line.box.fontSize).takeIf { it > 0f } ?: metrics.bodyFontSize
        val distance = line.box.bottom - previous.box.bottom
        return previous.pageIndex == line.pageIndex && distance > metrics.lineSpacing * size * GAP_RATIO
    }

    /** The font sizes of the two lines differ by more than [FONT_CHANGE_RATIO]. */
    fun changesFontSize(
        previous: TextLine,
        line: TextLine,
    ): Boolean = sizeRatio(previous, line) > FONT_CHANGE_RATIO

    /** [line] starts at least [INDENT_EM] of the body font size right of the column's left edge. */
    fun isIndented(line: TextLine): Boolean = line.box.left - metrics.column(line.pageIndex).left >= indentOf(line)

    /**
     * [line] keeps away from both column edges by at least [CENTERED_MIN_GAP_SHARE] of the column width and by the
     * same amount, within one em or [CENTERED_TOLERANCE_SHARE] of the width. The wide gaps keep an indented first line
     * of ragged text, which may end about as far from the edge as its indent, from looking centered.
     */
    fun isCentered(line: TextLine): Boolean {
        val column = metrics.column(line.pageIndex)
        val leftGap = line.box.left - column.left
        val rightGap = column.right - line.box.right
        val minimumGap = column.width * CENTERED_MIN_GAP_SHARE
        return leftGap >= minimumGap &&
            rightGap >= minimumGap &&
            abs(leftGap - rightGap) <= max(emOf(line), column.width * CENTERED_TOLERANCE_SHARE)
    }

    /**
     * [line] stops clearly short of the column's right edge: by more than [JUSTIFIED_SHORT_SHARE] of the column width
     * in justified text, or [RAGGED_SHORT_SHARE] in ragged text, where full lines end anywhere near the edge.
     */
    fun isShort(line: TextLine): Boolean {
        val column = metrics.column(line.pageIndex)
        val share = if (metrics.justified) JUSTIFIED_SHORT_SHARE else RAGGED_SHORT_SHARE
        return column.right - line.box.right > column.width * share
    }

    /** [line] is set in a font at least [LARGE_FONT_RATIO] times the body size. */
    fun isLargeFont(line: TextLine): Boolean =
        metrics.bodyFontSize > 0f && line.box.fontSize >= metrics.bodyFontSize * LARGE_FONT_RATIO

    /** Larger font size over smaller, or 1 when a size is unknown. */
    fun sizeRatio(
        first: TextLine,
        second: TextLine,
    ): Float {
        val smaller = min(first.box.fontSize, second.box.fontSize)
        return if (smaller > 0f) max(first.box.fontSize, second.box.fontSize) / smaller else 1f
    }

    private fun indentOf(line: TextLine): Float = INDENT_EM * emOf(line)

    private fun emOf(line: TextLine): Float =
        (metrics.bodyFontSize.takeIf { it > 0f } ?: line.box.fontSize).coerceAtLeast(MIN_EM)

    companion object {
        const val GAP_RATIO: Float = 1.3f
        const val FONT_CHANGE_RATIO: Float = 1.2f
        const val INDENT_EM: Float = 0.75f
        const val JUSTIFIED_SHORT_SHARE: Float = 0.05f
        const val RAGGED_SHORT_SHARE: Float = 0.2f

        /** Font size, relative to the body text, from which a short line is a heading whatever its wording. */
        const val LARGE_FONT_RATIO: Float = 1.4f
        private const val CENTERED_MIN_GAP_SHARE = 0.1f
        private const val CENTERED_TOLERANCE_SHARE = 0.05f
        private const val MIN_EM = 1f
    }
}
