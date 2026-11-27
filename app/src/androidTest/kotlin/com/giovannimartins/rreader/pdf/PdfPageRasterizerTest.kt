package com.giovannimartins.rreader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException

/** [PdfPageRasterizer] needs the platform's native PDF renderer, so it is tested on a device. */
@RunWith(AndroidJUnit4::class)
class PdfPageRasterizerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun drawsAPageOnWhiteAt300Dpi() {
        val scan = ScannedPdf.write(folder.newFile("scan.pdf"), listOf(ScannedPdf.PARAGRAPH))

        PdfPageRasterizer(scan, OcrLimits()).use { rasterizer ->
            val image = rasterizer.render(0)!!
            try {
                assertThat(image.pixelsPerPoint).isWithin(TOLERANCE).of(300f / 72f)
                assertThat(image.width).isEqualTo(ScannedPdf.PAGE_WIDTH)
                assertThat(image.height).isEqualTo(ScannedPdf.PAGE_HEIGHT)
                assertThat(image.bitmap.width).isAnyOf(1749, 1750)
                assertThat(image.bitmap.height).isEqualTo(2479)
                assertThat(image.bitmap.getPixel(1, 1) or OPAQUE).isEqualTo(Color.WHITE)
                assertThat(image.bitmap.hasDarkPixel()).isTrue()
            } finally {
                image.bitmap.recycle()
            }
        }
    }

    @Test
    fun keepsThePictureWithinTheSizeCap() {
        val scan = ScannedPdf.write(folder.newFile("scan.pdf"), listOf(ScannedPdf.PARAGRAPH))

        PdfPageRasterizer(scan, OcrLimits(maxBitmapSide = 600)).use { rasterizer ->
            val image = rasterizer.render(0)!!
            assertThat(image.bitmap.height).isAtMost(600)
            assertThat(image.bitmap.width).isLessThan(image.bitmap.height)
            image.bitmap.recycle()
        }
    }

    @Test
    fun drawsNothingForAPageOutsideTheDocument() {
        val scan = ScannedPdf.write(folder.newFile("scan.pdf"), listOf(ScannedPdf.PARAGRAPH))

        PdfPageRasterizer(scan, OcrLimits()).use { rasterizer ->
            assertThat(rasterizer.render(1)).isNull()
            assertThat(rasterizer.render(-1)).isNull()
        }
    }

    @Test
    fun refusesAFileThatIsNotAPdf() {
        val notPdf = folder.newFile("livro.pdf").apply { writeText("isto não é um PDF") }

        assertThrows(IOException::class.java) { PdfPageRasterizer(notPdf, OcrLimits()) }
    }

    private fun Bitmap.hasDarkPixel(): Boolean =
        (0 until height step SAMPLE_STEP).any { y ->
            (0 until width step SAMPLE_STEP).any { x -> Color.red(getPixel(x, y)) < DARK }
        }

    private companion object {
        const val TOLERANCE = 0.001f
        const val OPAQUE = 0xFF000000.toInt()
        const val SAMPLE_STEP = 3
        const val DARK = 128
    }
}
