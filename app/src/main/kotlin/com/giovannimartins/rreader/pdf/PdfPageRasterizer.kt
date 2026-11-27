package com.giovannimartins.rreader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File
import kotlin.math.max

/** One page drawn for text recognition: [bitmap] shows a page of [width] by [height] points at [pixelsPerPoint]. */
internal class PageImage(
    val bitmap: Bitmap,
    val pixelsPerPoint: Float,
    val width: Float,
    val height: Float,
)

/**
 * Draws the pages of one PDF for text recognition with the platform [PdfRenderer] (ADR 0015): one page at a time, on
 * a white background (pages are transparent where nothing is drawn), at the resolution [OcrLimits] allows. The
 * renderer is native code that cannot be interrupted, which is why it only draws files PdfBox has read within its
 * limits, a page at a time.
 *
 * Not thread-safe. The constructor throws [java.io.IOException] or [SecurityException] when the platform cannot open
 * the file.
 */
internal class PdfPageRasterizer(
    source: File,
    private val limits: OcrLimits,
) : Closeable {
    private val renderer = openRenderer(source)

    /**
     * Page [index] in a new bitmap that the caller recycles, or null when the page does not exist or has no area.
     * Throws [OutOfMemoryError] when the bitmap cannot be allocated and [IllegalStateException] when the platform fails
     * to draw the page.
     */
    fun render(index: Int): PageImage? {
        if (index !in 0 until renderer.pageCount) return null
        return renderer.openPage(index).use { page ->
            val width = page.width.toFloat()
            val height = page.height.toFloat()
            val scale = limits.pixelsPerPoint(width, height)
            if (scale > 0f) PageImage(draw(page, width, height, scale), scale, width, height) else null
        }
    }

    override fun close() = renderer.close()

    private fun draw(
        page: PdfRenderer.Page,
        width: Float,
        height: Float,
        scale: Float,
    ): Bitmap {
        val bitmap =
            createBitmap(
                max(1, (width * scale).toInt()),
                max(1, (height * scale).toInt()),
                Bitmap.Config.ARGB_8888,
            )
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } catch (e: IllegalStateException) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    private companion object {
        /** The renderer owns the descriptor and closes it; it is closed here only when the renderer cannot start. */
        @Suppress("TooGenericExceptionCaught") // Rethrown: only the descriptor must not leak.
        fun openRenderer(source: File): PdfRenderer {
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
        }
    }
}
