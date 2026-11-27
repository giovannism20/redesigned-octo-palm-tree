package com.giovannimartins.rreader.textkit.layout

import kotlin.math.max
import kotlin.math.min

/**
 * A line of text that OCR found on a picture of a page, with its box in the picture's pixels: origin at the top-left
 * corner, y growing downwards.
 */
public data class RecognizedLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Lines that OCR grouped together, usually a paragraph, a heading or a page number, in reading order. */
public data class RecognizedBlock(
    val lines: List<RecognizedLine>,
)

/**
 * Turns what OCR found on the picture of a page into the [PageLayout] that [LayoutBookBuilder] reads, in the page's
 * own units (PDF points), so pages read by OCR and pages with a text layer can be mixed in one document.
 *
 * - Boxes are divided by the picture's scale, in pixels per page unit, and clipped to the page. Lines without text,
 *   and lines whose box is empty once clipped, are dropped. Blocks and lines keep the recognizer's reading order.
 * - A line's `bottom` is the bottom edge of its box, below the baseline when the line has descenders.
 * - The font size is estimated from the box height, which for OCR spans only the glyphs drawn: on a line with
 *   lower-case letters, from ascenders to descenders, about [MIXED_CASE_HEIGHT] times the font size; on a line of
 *   capitals and digits, about [CAPITALS_HEIGHT] times. A line takes at least the median estimate of its block, so a
 *   short line without ascenders or descenders keeps the size of its paragraph.
 */
public object OcrPageLayout {
    /** Height of the box of a line with lower-case letters, as a share of its font size. */
    public const val MIXED_CASE_HEIGHT: Float = 1f

    /** Height of the box of a line of capitals or digits only, as a share of its font size. */
    public const val CAPITALS_HEIGHT: Float = 0.7f

    /**
     * The page at [index], [width] by [height] page units, from the [blocks] OCR found on its picture drawn at
     * [pixelsPerUnit] pixels per page unit.
     */
    public fun build(
        index: Int,
        width: Float,
        height: Float,
        pixelsPerUnit: Float,
        blocks: List<RecognizedBlock>,
    ): PageLayout {
        require(pixelsPerUnit > 0f && pixelsPerUnit.isFinite()) { "pixelsPerUnit must be positive" }
        require(width >= 0f && height >= 0f && width.isFinite() && height.isFinite()) { "Invalid page size" }
        val page = Page(width, height, pixelsPerUnit)
        return PageLayout(index, width, height, blocks.flatMap(page::lines))
    }

    /** One page's size and the scale of its picture. */
    private class Page(
        private val width: Float,
        private val height: Float,
        private val scale: Float,
    ) {
        fun lines(block: RecognizedBlock): List<LayoutLine> {
            val boxes = block.lines.mapNotNull(::box)
            if (boxes.isEmpty()) return emptyList()
            val blockSize = boxes.map(Box::fontSize).sorted()[boxes.size / 2]
            return boxes.map { box ->
                LayoutLine(box.text, box.left, box.top, box.right, box.bottom, max(box.fontSize, blockSize))
            }
        }

        /** The line's box in page units, clipped to the page, or null when it has no text or no area left. */
        private fun box(line: RecognizedLine): Box? {
            val left = (min(line.left, line.right) / scale).coerceIn(0f, width)
            val right = (max(line.left, line.right) / scale).coerceIn(0f, width)
            val top = (min(line.top, line.bottom) / scale).coerceIn(0f, height)
            val bottom = (max(line.top, line.bottom) / scale).coerceIn(0f, height)
            // Comparisons with NaN are false, so boxes with broken coordinates are dropped here too.
            return if (line.text.isNotBlank() && right > left && bottom > top) {
                Box(line.text, left, top, right, bottom)
            } else {
                null
            }
        }
    }

    private class Box(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val fontSize: Float = (bottom - top) / if (text.any(Char::isLowerCase)) MIXED_CASE_HEIGHT else CAPITALS_HEIGHT
    }
}
