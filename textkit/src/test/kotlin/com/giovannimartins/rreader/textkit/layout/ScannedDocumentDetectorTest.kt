package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScannedDocumentDetectorTest {
    private val textPage = { index: Int -> page(index) { story("Uma página com texto de verdade") } }
    private val imagePage = { index: Int -> page(index) {} }

    @Test
    fun `reports a document whose pages have no text as scanned`() {
        val scan = (0..4).map(imagePage)

        assertThat(ScannedDocumentDetector.isScanned(scan)).isTrue()
        assertThat(LayoutBookBuilder().build(BookMetadata("Livro"), scan))
            .isEqualTo(LayoutResult.Failure(LayoutError.ScannedDocument))
    }

    @Test
    fun `reports a document as scanned when more than half of its pages lack text`() {
        val mostlyImages = listOf(textPage(0), imagePage(1), textPage(2), imagePage(3), imagePage(4))
        val halfImages = listOf(textPage(0), imagePage(1), textPage(2), imagePage(3))

        assertThat(ScannedDocumentDetector.isScanned(mostlyImages)).isTrue()
        assertThat(ScannedDocumentDetector.isScanned(halfImages)).isFalse()
    }

    @Test
    fun `counts pages with only a few characters, or only a caption repeated in the margin, as scanned`() {
        val stamped =
            (0..3).map { index ->
                page(index) {
                    footer("Digitalizado com um aplicativo de celular")
                    line("p. 1", left = 300f, right = 320f)
                }
            }
        val fewLetters = page(0) { full("Poucas letras aqui") }
        val enoughLetters = page(0) { full("E agora há vinte e duas letras") }

        assertThat(ScannedDocumentDetector.isScanned(stamped)).isTrue()
        assertThat(ScannedDocumentDetector.isScanned(listOf(fewLetters))).isTrue()
        assertThat(ScannedDocumentDetector.isScanned(listOf(enoughLetters))).isFalse()
    }

    @Test
    fun `does not report an empty document as scanned`() {
        assertThat(ScannedDocumentDetector.isScanned(emptyList())).isFalse()
    }
}
