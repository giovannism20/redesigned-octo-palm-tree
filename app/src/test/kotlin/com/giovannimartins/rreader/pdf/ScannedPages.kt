package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.textkit.layout.OcrPageLayout
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.giovannimartins.rreader.textkit.layout.RecognizedBlock
import com.giovannimartins.rreader.textkit.layout.RecognizedLine
import java.io.File
import java.util.Collections
import kotlin.time.TimeSource

/**
 * What a recognizer reads on a scanned A5 page (420 x 595 points) drawn at 300 dpi: one block per paragraph, boxes
 * that span the glyphs drawn (ascenders to descenders; capitals only for headings and page numbers), body text of
 * size 12 on a 15-point pitch in a justified column from x = 50 to x = 370, starting at baseline 120.
 */
internal class ScannedPageSketch(
    private val index: Int,
) {
    private val blocks = mutableListOf<RecognizedBlock>()
    private var baseline = FIRST_BASELINE

    fun header(text: String) = block(capitals(text, left = LEFT, baseline = HEADER_BASELINE, size = MARGIN_SIZE))

    fun pageNumber(text: String) = block(capitals(text, left = 205f, baseline = FOOTER_BASELINE, size = MARGIN_SIZE))

    fun heading(text: String) {
        val width = text.length * HEADING_SIZE * CHARACTER_WIDTH
        block(capitals(text, left = (LEFT + RIGHT - width) / 2, baseline = baseline, size = HEADING_SIZE))
        baseline += 2 * PITCH
    }

    /** Justified lines; the first is indented unless [continued] from the previous page, and the last is short. */
    fun paragraph(
        vararg lines: String,
        continued: Boolean = false,
    ) {
        block(
            lines.mapIndexed { position, text ->
                val left = if (position == 0 && !continued) INDENT else LEFT
                val right = if (position == lines.lastIndex) left + text.length * BODY_CHARACTER else RIGHT
                val line = box(text, left, baseline - ASCENT * BODY_SIZE, right, baseline + DESCENT * BODY_SIZE)
                baseline += PITCH
                line
            },
        )
    }

    fun build(): PageLayout = OcrPageLayout.build(index, WIDTH, HEIGHT, SCALE, blocks)

    private fun capitals(
        text: String,
        left: Float,
        baseline: Float,
        size: Float,
    ): List<RecognizedLine> =
        listOf(box(text, left, baseline - CAPITAL_HEIGHT * size, left + text.length * size * CHARACTER_WIDTH, baseline))

    private fun box(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = RecognizedLine(text, left * SCALE, top * SCALE, right * SCALE, bottom * SCALE)

    private fun block(lines: List<RecognizedLine>) {
        blocks += RecognizedBlock(lines)
    }

    companion object {
        const val WIDTH = 420f
        const val HEIGHT = 595f
        const val SCALE = 300f / 72f
        private const val LEFT = 50f
        private const val INDENT = 68f
        private const val RIGHT = 370f
        private const val PITCH = 15f
        private const val FIRST_BASELINE = 120f
        private const val HEADER_BASELINE = 40f
        private const val FOOTER_BASELINE = 565f
        private const val BODY_SIZE = 12f
        private const val HEADING_SIZE = 18f
        private const val MARGIN_SIZE = 9f
        private const val BODY_CHARACTER = 6f
        private const val CHARACTER_WIDTH = 0.6f
        private const val ASCENT = 0.78f
        private const val DESCENT = 0.22f
        private const val CAPITAL_HEIGHT = 0.7f
    }
}

/** The page at [index] as a recognizer reads the scan [block] sketches. */
internal fun scannedPage(
    index: Int,
    block: ScannedPageSketch.() -> Unit,
): PageLayout = ScannedPageSketch(index).apply(block).build()

/** A recognizer that reads the [pages] it is given (blank pages otherwise) and records what it was asked. */
internal class ScriptedRecognizer(
    pages: List<PageLayout> = emptyList(),
) : PageTextRecognizer {
    private val byIndex = pages.associateBy(PageLayout::index)

    /** The pages recognized, in order; safe to read from another thread. */
    val requested: MutableList<Int> = Collections.synchronizedList(mutableListOf())

    override fun open(source: File): PageTextRecognizer.Session =
        object : PageTextRecognizer.Session {
            override fun recognize(pageIndex: Int): PageLayout {
                requested += pageIndex
                return byIndex[pageIndex] ?: blank(pageIndex)
            }

            override fun close() = Unit
        }

    private fun blank(index: Int) = PageLayout(index, ScannedPageSketch.WIDTH, ScannedPageSketch.HEIGHT, emptyList())
}

/** The text recognition stage with [recognizer], storing pages under [filesRoot]. */
internal fun pdfOcr(
    recognizer: PageTextRecognizer,
    filesRoot: File,
    limits: OcrLimits = OcrLimits(),
): PdfOcr = PdfOcr(recognizer, OcrPageStore(BookFileStore(filesRoot)), OcrGate(), limits, TimeSource.Monotonic)
