package com.giovannimartins.rreader.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Hostile, damaged and oversized PDFs for security requirement SR-4 (see `docs/security.md`). */
@RunWith(RobolectricTestRunner::class)
class PdfSecurityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `SR-4 refuses a document over the page limit before reading its text`() {
        val book = textPages(3)

        assertThat(
            extract(book, PdfLimits(maxPages = 2)),
        ).isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.MAX_PAGES)))
        assertThat(extract(book, PdfLimits(maxPages = 3))).isInstanceOf(PdfExtraction.Extracted::class.java)
    }

    @Test
    fun `SR-4 counts pages while reading, as the declared page count can lie`() {
        val lying = file()
        PDDocument.load(textPages(3).writeTo(file())).use { document ->
            document.pages.cosObject.setInt(COSName.COUNT, 2)
            document.save(lying)
        }
        PDDocument.load(lying).use { assertThat(it.numberOfPages).isEqualTo(2) }

        assertThat(PdfLayoutExtractor(context, PdfLimits(maxPages = 2), TimeSource.Monotonic).extract(lying))
            .isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.MAX_PAGES)))
    }

    @Test
    fun `SR-4 keeps checking the clock on a page of drawings or of glyphs beyond the page cap`() {
        val drawings = file()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A5)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                repeat(2_000) {
                    stream.addRect(10f, 10f, 5f, 5f)
                    stream.fill()
                }
            }
            document.save(drawings)
        }
        val longLine = PdfFixture(context).apply { page { line("x".repeat(3_000)) } }.writeTo(file())

        assertThat(PdfLayoutExtractor(context, PdfLimits(), clockExpiringAfter(reads = 1)).extract(drawings))
            .isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET)))
        assertThat(
            PdfLayoutExtractor(
                context,
                PdfLimits(maxGlyphsPerPage = 10),
                clockExpiringAfter(reads = 1),
            ).extract(longLine),
        ).isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET)))
    }

    @Test
    fun `SR-4 stops an extraction that runs out of time`() {
        val expired =
            object : TimeSource {
                override fun markNow(): TimeMark =
                    object : TimeMark {
                        override fun elapsedNow() = 1.hours
                    }
            }
        val extractor = PdfLayoutExtractor(context, PdfLimits(), expired)

        assertThat(extractor.extract(textPages(2).writeTo(file())))
            .isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET)))
    }

    @Test
    fun `SR-4 stops at the glyph limit and caps the glyphs read per page`() {
        val book = PdfFixture(context).apply { page { line("Uma linha de texto bem comprida.") } }

        assertThat(
            extract(book, PdfLimits(maxGlyphs = 10)),
        ).isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.MAX_GLYPHS)))
        val capped = extract(book, PdfLimits(maxGlyphsPerPage = 5)) as PdfExtraction.Extracted
        assertThat(
            capped.content.pages
                .single()
                .lines
                .single()
                .text,
        ).isEqualTo("Uma l")
    }

    @Test
    fun `SR-4 fails instead of buffering more than allowed`() {
        val limits = PdfLimits(mainMemoryBytes = 1_024, maxBufferBytes = 4_096)

        assertThat(extract(textPages(10), limits)).isEqualTo(failure(PdfError.LimitExceeded(PdfLimit.MEMORY)))
    }

    @Test
    fun `SR-4 stops when the import is cancelled`() {
        val file = textPages(2).writeTo(file())

        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) { PdfLayoutExtractor(context).extract(file) }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `refuses a document that needs a password`() {
        val locked =
            PdfFixture(context).apply {
                userPassword = "segredo"
                page { line("Texto protegido.") }
            }

        assertThat(extract(locked)).isEqualTo(failure(PdfError.PasswordProtected))
    }

    @Test
    fun `reports files that are not PDFs, truncated PDFs and PDFs without pages as damaged`() {
        val notPdf = file().apply { writeText("isto não é um PDF") }
        val garbage = file().apply { writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(2_048) { (it * 31).toByte() }) }
        val truncated = file().apply { writeBytes(textPages(2).bytes().copyOf(300)) }

        listOf(notPdf, garbage, truncated).forEach { damaged ->
            assertThat(PdfLayoutExtractor(context).extract(damaged)).isEqualTo(failure(PdfError.Damaged))
        }
    }

    @Test
    fun `keeps reading after a page whose content cannot be decoded`() {
        val file = textPages(3).writeTo(file())
        val damaged = file()
        PDDocument.load(file).use { document ->
            document
                .getPage(1)
                .contentStreams
                .next()
                .cosObject
                .setItem(COSName.FILTER, COSName.getPDFName("Nada"))
            document.save(damaged)
        }

        val pages = (PdfLayoutExtractor(context).extract(damaged) as PdfExtraction.Extracted).content.pages

        assertThat(pages.map { it.lines.size }).containsExactly(1, 0, 1).inOrder()
    }

    @Test
    fun `ends outline branches that loop back`() {
        val fixture =
            PdfFixture(context).apply {
                page { line("Primeira página.") }
                page { line("Segunda página.") }
                bookmark("Um", 0)
                bookmark("Dois", 1)
            }
        val looped = file()
        PDDocument.load(fixture.writeTo(file())).use { document ->
            val outline = document.documentCatalog.documentOutline
            outline.lastChild.cosObject.setItem(COSName.NEXT, outline.firstChild.cosObject)
            outline.firstChild.cosObject.setItem(COSName.FIRST, outline.firstChild.cosObject)
            document.save(looped)
        }

        val outline = (PdfLayoutExtractor(context).extract(looped) as PdfExtraction.Extracted).content.outline

        assertThat(outline.map { it.title }).containsExactly("Um", "Dois").inOrder()
    }

    /** A clock whose time is up after it has been read [reads] times. */
    private fun clockExpiringAfter(reads: Int) =
        object : TimeSource {
            override fun markNow(): TimeMark =
                object : TimeMark {
                    private var done = 0

                    override fun elapsedNow(): Duration = if (++done > reads) 1.hours else Duration.ZERO
                }
        }

    private fun textPages(count: Int) =
        PdfFixture(context).apply { repeat(count) { index -> page { line("Texto da página ${index + 1}.") } } }

    private fun extract(
        fixture: PdfFixture,
        limits: PdfLimits = PdfLimits(),
    ): PdfExtraction = PdfLayoutExtractor(context, limits, TimeSource.Monotonic).extract(fixture.writeTo(file()))

    private fun failure(error: PdfError) = PdfExtraction.Failed(error)

    private fun file(): File = folder.newFile()
}
