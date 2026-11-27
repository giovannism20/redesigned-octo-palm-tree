package com.giovannimartins.rreader.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Safety limits for recognizing the text of PDF pages on the device (security requirement SR-4, ADR 0015): a hostile
 * or merely gigantic scan must end in a typed failure, never in an endless import or an out-of-memory crash.
 */
data class OcrLimits(
    /** Books with more pages to recognize are refused before any page is drawn. */
    val maxPages: Int = DEFAULT_MAX_PAGES,
    /** Recognition of a book stops once its pages took this long, counting the pages stored by earlier runs. */
    val timeBudget: Duration = DEFAULT_TIME_BUDGET,
    /** How long the recognizer may take to read one page. */
    val pageTimeout: Duration = DEFAULT_PAGE_TIMEOUT,
    /** Resolution pages are drawn at for the recognizer, before the caps below. */
    val dotsPerInch: Int = DEFAULT_DOTS_PER_INCH,
    /** Longest side of a page picture, in pixels. */
    val maxBitmapSide: Int = DEFAULT_MAX_BITMAP_SIDE,
    /** Pixels in a page picture; at four bytes per pixel, this bounds the memory a page takes. */
    val maxBitmapPixels: Int = DEFAULT_MAX_BITMAP_PIXELS,
) {
    init {
        require(maxPages > 0 && dotsPerInch > 0 && maxBitmapSide > 0 && maxBitmapPixels > 0) {
            "Limits must be positive"
        }
        require(timeBudget.isPositive() && pageTimeout.isPositive()) { "Durations must be positive" }
    }

    /**
     * Pixels per point to draw a page of [width] by [height] points at: [dotsPerInch], reduced as needed so that the
     * picture fits [maxBitmapSide] and [maxBitmapPixels]. A page without a positive, finite size gets 0.
     */
    fun pixelsPerPoint(
        width: Float,
        height: Float,
    ): Float {
        if (!width.isMeasurable() || !height.isMeasurable()) return 0f
        val wanted = dotsPerInch / POINTS_PER_INCH
        val bySide = maxBitmapSide / max(width, height)
        val byPixels = sqrt(maxBitmapPixels / (width.toDouble() * height)).toFloat()
        return min(wanted, min(bySide, byPixels))
    }

    private fun Float.isMeasurable(): Boolean = this > 0f && isFinite()

    private companion object {
        const val POINTS_PER_INCH = 72f
        const val DEFAULT_MAX_PAGES = 1_500
        val DEFAULT_TIME_BUDGET = 60.minutes
        val DEFAULT_PAGE_TIMEOUT = 1.minutes
        const val DEFAULT_DOTS_PER_INCH = 300
        const val DEFAULT_MAX_BITMAP_SIDE = 4_096
        const val DEFAULT_MAX_BITMAP_PIXELS = 12_000_000
    }
}
