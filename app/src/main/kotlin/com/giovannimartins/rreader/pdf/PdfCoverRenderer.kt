package com.giovannimartins.rreader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.model.CoverImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a cover for a PDF book. A failure only means the book has no cover, so it returns null instead of failing. */
fun interface PdfCoverRenderer {
    fun render(source: File): CoverImage?
}

/**
 * Renders the first page with the platform [PdfRenderer] into a JPEG that fits [MAX_WIDTH] x [MAX_HEIGHT] pixels,
 * on a white background (pages are transparent where nothing is drawn).
 */
class FirstPageCoverRenderer
    @Inject
    constructor() : PdfCoverRenderer {
        override fun render(source: File): CoverImage? =
            try {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use(::renderFirstPage)
                }
            } catch (e: IOException) {
                noCover(e)
            } catch (e: SecurityException) {
                noCover(e)
            } catch (e: IllegalStateException) {
                noCover(e)
            } catch (e: IllegalArgumentException) {
                noCover(e)
            }

        private fun renderFirstPage(renderer: PdfRenderer): CoverImage? =
            if (renderer.pageCount == 0) {
                null
            } else {
                renderer.openPage(0).use { page ->
                    if (page.width <= 0 || page.height <= 0) null else draw(page)
                }
            }

        private fun draw(page: PdfRenderer.Page): CoverImage {
            val scale = min(MAX_WIDTH.toFloat() / page.width, MAX_HEIGHT.toFloat() / page.height)
            val bitmap =
                createBitmap(
                    (page.width * scale).roundToInt().coerceAtLeast(1),
                    (page.height * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            return try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val jpeg = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)
                CoverImage(MEDIA_TYPE, jpeg.toByteArray())
            } finally {
                bitmap.recycle()
            }
        }

        private fun noCover(error: Exception): CoverImage? {
            Diagnostics.recordHandled("render PDF cover", error)
            return null
        }

        companion object {
            const val MAX_WIDTH: Int = 600
            const val MAX_HEIGHT: Int = 900
            private const val JPEG_QUALITY = 85
            private const val MEDIA_TYPE = "image/jpeg"
        }
    }
