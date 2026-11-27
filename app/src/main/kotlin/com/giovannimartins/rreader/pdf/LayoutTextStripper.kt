package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.layout.LayoutLine
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorName
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Collects every page of a document as a [PageLayout] instead of writing plain text.
 *
 * PdfBox's own line building is kept: it decides which glyphs form a line, inserts the spaces PDFs often leave out,
 * merges accents and expands ligatures. The stripper keeps the content stream order (no sorting by position), which
 * is the reading order of well-made PDFs and keeps columns apart. Each line keeps its text, its bounding box in page
 * coordinates with the origin at the top-left corner and the baseline as bottom, and the median size of its glyphs as
 * drawn (font size times text and page scaling).
 *
 * A page that PdfBox fails to read is kept with the lines read so far, so one damaged page does not lose the book;
 * reaching the buffer limit, though, stops the extraction ([ExtractionStopped]).
 *
 * The stripper also notes the [picturedPages]: pages whose text may be a picture rather than a text layer, because
 * they draw an image, at least [MIN_SHAPE_SEGMENTS] path segments (text turned into outlines), or could not be read
 * completely. Blank pages and pages of plain text are not among them.
 */
internal class LayoutTextStripper(
    private val budget: ExtractionBudget,
    private val maxGlyphsPerPage: Int,
) : PDFTextStripper() {
    private val collected = mutableListOf<PageLayout>()
    private val pictured = mutableSetOf<Int>()
    private val line = LineBuilder()
    private var pageLines = mutableListOf<LayoutLine>()
    private var pageWidth = 0f
    private var pageHeight = 0f
    private var pageGlyphs = 0
    private var pageDrawsImage = false
    private var pageShapeSegments = 0
    private var pageOpen = false

    /** The pages read so far, in document order. */
    val pages: List<PageLayout> get() = collected

    /** Indexes of the pages read so far whose text may be pictured: see the class documentation. */
    val picturedPages: Set<Int> get() = pictured

    override fun processPage(page: PDPage) {
        try {
            super.processPage(page)
        } catch (e: IOException) {
            if (e.isBufferLimit()) throw ExtractionStopped(PdfLimit.MEMORY, e)
            Diagnostics.recordHandled("read PDF page", e)
            if (pageOpen) endPage(page) else collected += PageLayout(currentPageNo - 1, 0f, 0f, emptyList())
            pictured += currentPageNo - 1
        }
    }

    override fun startPage(page: PDPage) {
        budget.startPage()
        val box = page.cropBox
        val turned = page.rotation % HALF_TURN != 0
        pageWidth = abs(if (turned) box.height else box.width).finiteOrZero()
        pageHeight = abs(if (turned) box.width else box.height).finiteOrZero()
        pageLines = mutableListOf()
        pageGlyphs = 0
        pageDrawsImage = false
        pageShapeSegments = 0
        pageOpen = true
        line.clear()
    }

    override fun processOperator(
        operator: Operator,
        operands: MutableList<COSBase>,
    ) {
        budget.countOperator()
        noteDrawing(operator.name, operands)
        super.processOperator(operator, operands)
    }

    /** Notes images (also inline ones and those inside forms, whose operators come here too) and path segments. */
    private fun noteDrawing(
        operator: String,
        operands: List<COSBase>,
    ) {
        when (operator) {
            OperatorName.BEGIN_INLINE_IMAGE -> pageDrawsImage = true
            OperatorName.DRAW_OBJECT -> if (isImage(operands.firstOrNull())) pageDrawsImage = true
            in PATH_SEGMENTS -> pageShapeSegments++
        }
    }

    /** Whether [name] names an image XObject; PdfBox's own text extraction checks the same before drawing forms. */
    private fun isImage(name: COSBase?): Boolean = name is COSName && resources?.isImageXObject(name) == true

    override fun processTextPosition(text: TextPosition) {
        budget.countGlyph()
        if (pageGlyphs < maxGlyphsPerPage) {
            pageGlyphs++
            super.processTextPosition(text)
        }
    }

    override fun writeString(
        text: String,
        textPositions: MutableList<TextPosition>,
    ) {
        line.add(text, textPositions)
    }

    override fun writeWordSeparator() {
        line.addSpace()
    }

    override fun writeLineSeparator() {
        line.build()?.let(pageLines::add)
    }

    override fun endPage(page: PDPage) {
        line.build()?.let(pageLines::add)
        collected += PageLayout(currentPageNo - 1, pageWidth, pageHeight, pageLines)
        if (pageDrawsImage || pageShapeSegments >= MIN_SHAPE_SEGMENTS) pictured += currentPageNo - 1
        pageOpen = false
    }

    /** Accumulates the words and glyph boxes of the line being written. */
    private class LineBuilder {
        private val text = StringBuilder()
        private val sizes = mutableListOf<Float>()
        private var left = Float.POSITIVE_INFINITY
        private var top = Float.POSITIVE_INFINITY
        private var right = Float.NEGATIVE_INFINITY
        private var bottom = Float.NEGATIVE_INFINITY

        fun add(
            word: String,
            positions: List<TextPosition>,
        ) {
            text.append(word)
            positions.forEach(::include)
        }

        fun addSpace() {
            if (text.isNotEmpty()) text.append(' ')
        }

        /** The finished line, or null when it has no text or no usable glyph position; starts the next line. */
        fun build(): LayoutLine? {
            val built =
                if (text.isBlank() || sizes.isEmpty()) {
                    null
                } else {
                    LayoutLine(text.toString(), left, top, right, bottom, sizes.sorted()[sizes.size / 2])
                }
            clear()
            return built
        }

        fun clear() {
            text.setLength(0)
            sizes.clear()
            left = Float.POSITIVE_INFINITY
            top = Float.POSITIVE_INFINITY
            right = Float.NEGATIVE_INFINITY
            bottom = Float.NEGATIVE_INFINITY
        }

        /** Grows the line's box by one glyph; glyphs with broken coordinates (hostile or damaged files) are skipped. */
        private fun include(position: TextPosition) {
            val size = abs(position.yScale).takeIf { it > 0f && it.isFinite() } ?: position.fontSizeInPt
            val x = position.xDirAdj
            val baseline = position.yDirAdj
            val width = position.widthDirAdj
            val height = position.heightDir.takeIf { it > 0f && it.isFinite() } ?: (size * ASCENT_RATIO)
            val finite = size.isFinite() && x.isFinite() && baseline.isFinite() && width.isFinite() && height.isFinite()
            if (finite && size >= 0f) {
                left = min(left, x)
                right = max(right, x + max(width, 0f))
                top = min(top, baseline - height)
                bottom = max(bottom, baseline)
                sizes += size
            }
        }
    }

    companion object {
        /** Path segments from which a page may be text drawn as outlines; a blank page or a rule has a handful. */
        const val MIN_SHAPE_SEGMENTS: Int = 1_000

        private val PATH_SEGMENTS =
            setOf(
                OperatorName.MOVE_TO,
                OperatorName.LINE_TO,
                OperatorName.CURVE_TO,
                OperatorName.CURVE_TO_REPLICATE_INITIAL_POINT,
                OperatorName.CURVE_TO_REPLICATE_FINAL_POINT,
                OperatorName.APPEND_RECT,
            )
        private const val HALF_TURN = 180

        /** Share of the font size above the baseline, used when a font gives no glyph height. */
        private const val ASCENT_RATIO = 0.7f

        private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
    }
}
