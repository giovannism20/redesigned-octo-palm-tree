package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OcrPageLayoutTest {
    @Test
    fun `scales boxes from the picture's pixels to page units, in the recognizer's order`() {
        val page =
            OcrPageLayout.build(
                index = 3,
                width = 420f,
                height = 595f,
                pixelsPerUnit = 2f,
                blocks =
                    listOf(
                        RecognizedBlock(listOf(RecognizedLine("Segundo bloco", 100f, 200f, 300f, 220f))),
                        RecognizedBlock(listOf(RecognizedLine("Primeiro bloco", 100f, 100f, 300f, 120f))),
                    ),
            )

        assertThat(page.index).isEqualTo(3)
        assertThat(page.width).isEqualTo(420f)
        assertThat(page.height).isEqualTo(595f)
        assertThat(page.lines)
            .containsExactly(
                LayoutLine("Segundo bloco", 50f, 100f, 150f, 110f, 10f),
                LayoutLine("Primeiro bloco", 50f, 50f, 150f, 60f, 10f),
            ).inOrder()
    }

    @Test
    fun `estimates the font size of capitals and digits from their shorter boxes`() {
        val lines =
            build(
                RecognizedBlock(listOf(RecognizedLine("CAPÍTULO IV", 0f, 0f, 100f, 14f))),
                RecognizedBlock(listOf(RecognizedLine("Texto corrido", 0f, 20f, 100f, 30f))),
                RecognizedBlock(listOf(RecognizedLine("— 12 —", 0f, 40f, 100f, 47f))),
            )

        assertThat(lines.map { it.fontSize })
            .containsExactly(14f / OcrPageLayout.CAPITALS_HEIGHT, 10f, 7f / OcrPageLayout.CAPITALS_HEIGHT)
            .inOrder()
    }

    @Test
    fun `gives a short line the size of its paragraph but keeps a larger line's own size`() {
        val paragraph =
            RecognizedBlock(
                listOf(
                    RecognizedLine("Uma linha com hastes e caudas,", 0f, 0f, 300f, 12f),
                    RecognizedLine("e com um ar sereno", 0f, 15f, 200f, 21f),
                    RecognizedLine("Uma linha de título", 0f, 30f, 300f, 48f),
                    RecognizedLine("que segue o parágrafo.", 0f, 51f, 250f, 63f),
                ),
            )

        assertThat(build(paragraph).map { it.fontSize }).containsExactly(12f, 12f, 18f, 12f).inOrder()
    }

    @Test
    fun `clips boxes to the page and drops lines without text or area`() {
        val lines =
            build(
                RecognizedBlock(
                    listOf(
                        RecognizedLine("Sai da página", -20f, 590f, 500f, 610f),
                        RecognizedLine("   ", 10f, 10f, 100f, 20f),
                        RecognizedLine("Fora da página", 500f, 10f, 600f, 20f),
                        RecognizedLine("Sem altura", 10f, 30f, 100f, 30f),
                        RecognizedLine("Quebrada", Float.NaN, 40f, 100f, 50f),
                    ),
                ),
            )

        assertThat(lines).containsExactly(LayoutLine("Sai da página", 0f, 590f, 420f, 595f, 5f))
    }

    @Test
    fun `refuses a picture scale or a page size that cannot be measured`() {
        assertThrows(IllegalArgumentException::class.java) { OcrPageLayout.build(0, 420f, 595f, 0f, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            OcrPageLayout.build(0, Float.NaN, 595f, 1f, emptyList())
        }
    }

    @Test
    fun `OCR pages become chapters and paragraphs like pages with a text layer`() {
        val pages =
            listOf(
                scannedPage(0) {
                    header("DOM CASMURRO")
                    heading("CAPÍTULO I")
                    paragraph(
                        "Uma noite destas, vindo da cidade para o",
                        "Engenho Novo, encontrei no trem um rapaz",
                        "que me cumprimentou e quis fazê-",
                        "lo de novo na estação seguinte.",
                    )
                    pageNumber("1")
                },
                scannedPage(1) {
                    header("DOM CASMURRO")
                    paragraph(
                        "No dia seguinte voltei a vê-lo na plataforma,",
                        "e ele continuou a falar sem parar, sempre",
                        "com o mesmo ar sereno, de um modo",
                        "que a viagem pareceu curta.",
                        shortLine = 2,
                    )
                    pageNumber("2")
                },
                scannedPage(2) {
                    header("DOM CASMURRO")
                    heading("CAPÍTULO II")
                    paragraph("— Boa noite — disse ele.")
                    pageNumber("3")
                },
            )

        val result = LayoutBookBuilder().build(BookMetadata("Dom Casmurro"), pages)

        assertThat((result as LayoutResult.Success).book.chapters)
            .containsExactly(
                Chapter.fromParagraphs(
                    "CAPÍTULO I",
                    listOf(
                        "CAPÍTULO I",
                        "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem um rapaz que me " +
                            "cumprimentou e quis fazê-lo de novo na estação seguinte.",
                        "No dia seguinte voltei a vê-lo na plataforma, e ele continuou a falar sem parar, sempre " +
                            "com o mesmo ar sereno, de um modo que a viagem pareceu curta.",
                    ),
                ),
                Chapter.fromParagraphs("CAPÍTULO II", listOf("CAPÍTULO II", "— Boa noite — disse ele.")),
            ).inOrder()
    }

    @Test
    fun `pages where OCR found nothing still make a scanned document`() {
        val pages = (0..2).map { index -> OcrPageLayout.build(index, 420f, 595f, SCALE, emptyList()) }

        assertThat(LayoutBookBuilder().build(BookMetadata("Livro"), pages))
            .isEqualTo(LayoutResult.Failure(LayoutError.ScannedDocument))
    }

    private fun build(vararg blocks: RecognizedBlock): List<LayoutLine> =
        OcrPageLayout.build(0, 420f, 595f, 1f, blocks.toList()).lines

    /**
     * An A5 page (420 x 595 points) of a typeset book as a recognizer reports it at 300 dpi: one block per paragraph,
     * boxes that span the glyphs drawn (ascenders to descenders, capitals only for headings and page numbers), body
     * text of size 12 on a 15-point pitch in a justified column from x = 50 to x = 370.
     */
    private class ScannedPageSketch {
        val blocks = mutableListOf<RecognizedBlock>()
        private var baseline = 120f

        fun header(text: String) = block(capitals(text, left = 50f, baseline = 40f, size = MARGIN_SIZE))

        fun pageNumber(text: String) = block(capitals(text, left = 205f, baseline = 565f, size = MARGIN_SIZE))

        fun heading(text: String) {
            val width = text.length * HEADING_SIZE * CHARACTER_WIDTH
            block(capitals(text, left = (LEFT + RIGHT - width) / 2, baseline = baseline, size = HEADING_SIZE))
            baseline += 2 * PITCH
        }

        /** Justified lines, the first indented and the last short; [shortLine] has no ascenders or descenders. */
        fun paragraph(
            vararg lines: String,
            shortLine: Int = -1,
        ) {
            block(
                lines.mapIndexed { position, text ->
                    val left = if (position == 0) INDENT else LEFT
                    val right = if (position == lines.lastIndex) left + text.length * BODY_CHARACTER else RIGHT
                    val box =
                        if (position == shortLine) {
                            Box(left, baseline - X_HEIGHT * BODY_SIZE, right, baseline)
                        } else {
                            Box(left, baseline - ASCENT * BODY_SIZE, right, baseline + DESCENT * BODY_SIZE)
                        }
                    baseline += PITCH
                    box.toLine(text)
                },
            )
        }

        private fun capitals(
            text: String,
            left: Float,
            baseline: Float,
            size: Float,
        ): List<RecognizedLine> {
            val width = text.length * size * CHARACTER_WIDTH
            return listOf(Box(left, baseline - CAPITAL_HEIGHT * size, left + width, baseline).toLine(text))
        }

        private fun block(lines: List<RecognizedLine>) {
            blocks += RecognizedBlock(lines)
        }

        private class Box(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
        ) {
            fun toLine(text: String) = RecognizedLine(text, left * SCALE, top * SCALE, right * SCALE, bottom * SCALE)
        }
    }

    private fun scannedPage(
        index: Int,
        block: ScannedPageSketch.() -> Unit,
    ): PageLayout = OcrPageLayout.build(index, 420f, 595f, SCALE, ScannedPageSketch().apply(block).blocks)

    private companion object {
        const val SCALE = 300f / 72f
        const val LEFT = 50f
        const val INDENT = 68f
        const val RIGHT = 370f
        const val PITCH = 15f
        const val BODY_SIZE = 12f
        const val HEADING_SIZE = 18f
        const val MARGIN_SIZE = 9f
        const val BODY_CHARACTER = 6f
        const val CHARACTER_WIDTH = 0.6f
        const val ASCENT = 0.78f
        const val DESCENT = 0.22f
        const val X_HEIGHT = 0.5f
        const val CAPITAL_HEIGHT = 0.7f
    }
}
