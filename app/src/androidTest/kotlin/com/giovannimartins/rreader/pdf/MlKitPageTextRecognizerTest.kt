package com.giovannimartins.rreader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** ML Kit's bundled recognizer and the platform renderer run only on a device. */
@RunWith(AndroidJUnit4::class)
class MlKitPageTextRecognizerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun readsTheTextOfAScannedPageInPdfPoints() {
        val scan = ScannedPdf.write(folder.newFile("scan.pdf"), listOf(ScannedPdf.PARAGRAPH))

        val page = MlKitPageTextRecognizer().open(scan).use { it.recognize(0) }

        assertThat(page.index).isEqualTo(0)
        assertThat(page.width).isEqualTo(ScannedPdf.PAGE_WIDTH)
        assertThat(page.height).isEqualTo(ScannedPdf.PAGE_HEIGHT)
        val text = page.lines.joinToString(" ") { it.text }
        assertThat(text).contains("noite destas")
        assertThat(text).contains("Engenho Novo")
        page.lines.forEach { line ->
            assertThat(line.left).isAtLeast(0f)
            assertThat(line.right).isAtMost(page.width)
            assertThat(line.top).isAtLeast(0f)
            assertThat(line.bottom).isAtMost(page.height)
            // The estimate from the box height should land near the 12 points the text was drawn at.
            assertThat(line.fontSize).isWithin(FONT_SIZE_TOLERANCE).of(ScannedPdf.BODY_SIZE)
        }
        val first = page.lines.first()
        assertThat(first.left).isWithin(POSITION_TOLERANCE).of(ScannedPdf.LEFT)
        assertThat(first.bottom).isWithin(POSITION_TOLERANCE).of(ScannedPdf.FIRST_BASELINE)
    }

    @Test
    fun findsNoLinesOnABlankPage() {
        val blank = ScannedPdf.write(folder.newFile("blank.pdf"), listOf(emptyList()))

        val page = MlKitPageTextRecognizer().open(blank).use { it.recognize(0) }

        assertThat(page.lines).isEmpty()
    }

    @Test
    fun refusesAFileThePlatformCannotOpen() {
        val notPdf = folder.newFile("livro.pdf").apply { writeText("isto não é um PDF") }

        val failure = assertThrows(OcrException::class.java) { MlKitPageTextRecognizer().open(notPdf) }

        assertThat(failure.error).isEqualTo(PdfError.Damaged)
    }

    private companion object {
        const val FONT_SIZE_TOLERANCE = 4f
        const val POSITION_TOLERANCE = 6f
    }
}
