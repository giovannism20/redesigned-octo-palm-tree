package com.giovannimartins.rreader.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.FIRST_BASELINE
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.FOOTER_BASELINE
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.LEADING
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.LEFT
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfLayoutExtractorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `extracts every line with its box and drawn font size, measured from the top-left corner`() {
        val fixture =
            PdfFixture(context).apply {
                page {
                    line("Primeira linha, com acentuação e cedilha.")
                    line("Segunda linha, maior.", size = 18f)
                    at(FOOTER_BASELINE, "1", x = 205f)
                }
            }

        val page = extract(fixture).pages.single()

        assertThat(page.index).isEqualTo(0)
        assertThat(page.width).isWithin(TOLERANCE).of(PDRectangle.A5.width)
        assertThat(page.height).isWithin(TOLERANCE).of(PDRectangle.A5.height)
        assertThat(page.lines.map { it.text })
            .containsExactly("Primeira linha, com acentuação e cedilha.", "Segunda linha, maior.", "1")
            .inOrder()
        val (first, second, footer) = page.lines
        assertThat(first.left).isWithin(TOLERANCE).of(LEFT)
        assertThat(first.bottom).isWithin(TOLERANCE).of(FIRST_BASELINE)
        assertThat(first.top).isLessThan(first.bottom)
        assertThat(first.right).isGreaterThan(first.left + 150f)
        assertThat(first.fontSize).isWithin(TOLERANCE).of(12f)
        assertThat(second.bottom).isWithin(TOLERANCE).of(FIRST_BASELINE + LEADING)
        assertThat(second.fontSize).isWithin(TOLERANCE).of(18f)
        assertThat(footer.bottom).isWithin(TOLERANCE).of(FOOTER_BASELINE)
    }

    @Test
    fun `reports every page, also those without text, and rotated pages with their displayed size`() {
        val fixture =
            PdfFixture(context).apply {
                rotation = 90
                page { line("Texto na página girada.") }
                picturePage()
            }

        val pages = extract(fixture).pages

        assertThat(pages.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(pages[0].width).isWithin(TOLERANCE).of(PDRectangle.A5.height)
        assertThat(pages[0].height).isWithin(TOLERANCE).of(PDRectangle.A5.width)
        assertThat(pages[1].lines).isEmpty()
    }

    @Test
    fun `notes the pages whose text may be a picture, and not blank or text pages`() {
        val fixture =
            PdfFixture(context).apply {
                page { line("Uma página com texto de verdade.") }
                picturePage()
                blankPage()
                outlinedTextPage()
                outlinedTextPage(segments = LayoutTextStripper.MIN_SHAPE_SEGMENTS - 1)
            }

        val content = extract(fixture)

        assertThat(content.pages).hasSize(5)
        assertThat(content.picturedPages).containsExactly(1, 3)
    }

    @Test
    fun `reads the outline in document order with target pages and positions`() {
        val fixture =
            PdfFixture(context).apply {
                repeat(3) { index -> page { line("Página ${index + 1} do livro.") } }
                bookmark(
                    "Parte I",
                    0,
                    children = listOf(bookmark("Capítulo 1", 0, top = 80f), bookmark("Capítulo 2", 2, top = 300f)),
                )
                bookmark("Notas", 1)
            }

        val outline = extract(fixture).outline

        assertThat(outline.map { it.title to it.pageIndex })
            .containsExactly("Parte I" to 0, "Capítulo 1" to 0, "Capítulo 2" to 2, "Notas" to 1)
            .inOrder()
        assertThat(outline[0].top).isNull()
        assertThat(outline[1].top!!).isWithin(1f).of(80f)
        assertThat(outline[2].top!!).isWithin(1f).of(300f)
    }

    @Test
    fun `reads the document information and the catalog language`() {
        val fixture =
            PdfFixture(context).apply {
                title = "Dom Casmurro"
                author = "Machado de Assis"
                language = "pt-BR"
                page { line("Texto.") }
            }

        assertThat(extract(fixture).info).isEqualTo(PdfInfo("Dom Casmurro", "Machado de Assis", "pt-BR"))
    }

    @Test
    fun `opens documents that only restrict permissions with an owner password`() {
        val fixture =
            PdfFixture(context).apply {
                userPassword = ""
                ownerPassword = "dono"
                page { line("Texto que se pode ler em voz alta.") }
            }

        assertThat(
            extract(fixture)
                .pages
                .single()
                .lines
                .single()
                .text,
        ).isEqualTo("Texto que se pode ler em voz alta.")
    }

    private fun extract(fixture: PdfFixture): PdfContent {
        val result = PdfLayoutExtractor(context).extract(fixture.writeTo(folder.newFile("livro.pdf")))
        assertThat(result).isInstanceOf(PdfExtraction.Extracted::class.java)
        return (result as PdfExtraction.Extracted).content
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
