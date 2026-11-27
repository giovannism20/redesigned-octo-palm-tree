package com.giovannimartins.rreader.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** [FirstPageCoverRenderer] needs the platform's native PDF renderer, so it is tested on a device. */
@RunWith(AndroidJUnit4::class)
class FirstPageCoverRendererTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun rendersTheFirstPageIntoADownscaledJpegOnWhite() {
        val cover = FirstPageCoverRenderer().render(twoPageBook())

        assertNotNull(cover)
        assertEquals("image/jpeg", cover!!.mediaType)
        val bitmap = BitmapFactory.decodeByteArray(cover.bytes, 0, cover.bytes.size)
        assertTrue(bitmap.width <= FirstPageCoverRenderer.MAX_WIDTH)
        assertTrue(bitmap.height <= FirstPageCoverRenderer.MAX_HEIGHT)
        assertEquals(
            PDRectangle.A5.width / PDRectangle.A5.height,
            bitmap.width.toFloat() / bitmap.height,
            RATIO_TOLERANCE,
        )
        assertEquals(Color.WHITE, bitmap.getPixel(1, 1) or OPAQUE)
        assertTrue("the title should darken some pixels", bitmap.hasDarkPixel())
    }

    @Test
    fun returnsNoCoverForAFileThatIsNotAPdf() {
        val notPdf = folder.newFile("livro.pdf").apply { writeText("isto não é um PDF") }

        assertNull(FirstPageCoverRenderer().render(notPdf))
    }

    private fun twoPageBook(): File {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val file = folder.newFile("livro.pdf")
        PDDocument().use { document ->
            val cover = PDPage(PDRectangle.A5)
            document.addPage(cover)
            PDPageContentStream(document, cover).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, TITLE_SIZE)
                stream.newLineAtOffset(TITLE_X, TITLE_Y)
                stream.showText("IRACEMA")
                stream.endText()
            }
            document.addPage(PDPage(PDRectangle.A5))
            document.save(file)
        }
        return file
    }

    private fun Bitmap.hasDarkPixel(): Boolean =
        (0 until height step SAMPLE_STEP).any { y ->
            (0 until width step SAMPLE_STEP).any { x -> Color.red(getPixel(x, y)) < DARK }
        }

    private companion object {
        const val TITLE_SIZE = 48f
        const val TITLE_X = 60f
        const val TITLE_Y = 420f
        const val RATIO_TOLERANCE = 0.01f
        const val OPAQUE = 0xFF000000.toInt()
        const val SAMPLE_STEP = 2
        const val DARK = 128
    }
}
