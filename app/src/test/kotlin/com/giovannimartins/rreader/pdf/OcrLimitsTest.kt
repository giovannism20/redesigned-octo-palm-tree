package com.giovannimartins.rreader.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration

/** Page picture sizes for text recognition (SR-4). */
class OcrLimitsTest {
    private val limits = OcrLimits()

    @Test
    fun `SR-4 draws book pages at 300 dpi`() {
        val scale = limits.pixelsPerPoint(A5_WIDTH, A5_HEIGHT)

        assertThat(scale).isWithin(TOLERANCE).of(300f / 72f)
        val (width, height) = pixels(A5_WIDTH, A5_HEIGHT, scale)
        assertThat(width).isAnyOf(1749, 1750)
        assertThat(height).isEqualTo(2479)
    }

    @Test
    fun `SR-4 keeps the longest side of huge pages within the cap`() {
        // Scanners often declare a page as large as the picture's pixels, here a 300 dpi A4 scan.
        val scale = limits.pixelsPerPoint(2480f, 3508f)

        val (width, height) = pixels(2480f, 3508f, scale)
        assertThat(height).isAtMost(4_096)
        assertThat(height).isAtLeast(4_090)
        assertThat(width.toLong() * height).isAtMost(12_000_000L)
    }

    @Test
    fun `SR-4 keeps the pixels of huge square pages within the cap`() {
        val scale = limits.pixelsPerPoint(10_000f, 10_000f)

        val (width, height) = pixels(10_000f, 10_000f, scale)
        assertThat(width.toLong() * height).isAtMost(12_000_000L)
        assertThat(width.toLong() * height).isAtLeast(11_990_000L)
    }

    @Test
    fun `gives no scale to pages without a measurable size`() {
        listOf(0f to 595f, 420f to -1f, Float.NaN to 595f, 420f to Float.POSITIVE_INFINITY).forEach { (w, h) ->
            assertThat(limits.pixelsPerPoint(w, h)).isEqualTo(0f)
        }
    }

    @Test
    fun `refuses limits that would not limit anything`() {
        assertThrows(IllegalArgumentException::class.java) { OcrLimits(maxPages = 0) }
        assertThrows(IllegalArgumentException::class.java) { OcrLimits(maxBitmapPixels = 0) }
        assertThrows(IllegalArgumentException::class.java) { OcrLimits(timeBudget = Duration.ZERO) }
    }

    /** The bitmap size [PdfPageRasterizer] creates for a page of [width] by [height] points at [scale]. */
    private fun pixels(
        width: Float,
        height: Float,
        scale: Float,
    ): Pair<Int, Int> = (width * scale).toInt() to (height * scale).toInt()

    private companion object {
        const val A5_WIDTH = 420f
        const val A5_HEIGHT = 595f
        const val TOLERANCE = 0.0001f
    }
}
