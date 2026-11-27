package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.layout.OcrPageLayout
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.giovannimartins.rreader.textkit.layout.RecognizedBlock
import com.giovannimartins.rreader.textkit.layout.RecognizedLine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * [PageTextRecognizer] that draws pages with [PdfPageRasterizer] and reads them with ML Kit's Latin text recognizer,
 * whose model is bundled in the app, so nothing is downloaded and everything stays on the phone (ADR 0015).
 *
 * Each page waits at most [OcrLimits.pageTimeout] for ML Kit. A page's bitmap is recycled once ML Kit is done with
 * it; when the wait is interrupted or times out, ML Kit may still be reading it, so it is left to the garbage
 * collector instead. Never logs recognized text or file names (SR-8).
 */
class MlKitPageTextRecognizer internal constructor(
    private val limits: OcrLimits,
) : PageTextRecognizer {
    @Inject
    constructor() : this(OcrLimits())

    override fun open(source: File): PageTextRecognizer.Session {
        val rasterizer = openRasterizer(source)
        val recognizer =
            try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            } catch (e: IllegalStateException) {
                rasterizer.close()
                throw OcrException(PdfError.TextRecognitionFailed, e)
            }
        return Session(rasterizer, recognizer)
    }

    /** The platform renderer for [source]; a file it refuses is damaged, or encrypted in a way it cannot open. */
    private fun openRasterizer(source: File): PdfPageRasterizer {
        val failure =
            try {
                return PdfPageRasterizer(source, limits)
            } catch (e: IOException) {
                e
            } catch (e: SecurityException) {
                e
            } catch (e: IllegalArgumentException) {
                e
            } catch (e: IllegalStateException) {
                e
            }
        throw OcrException(if (failure is SecurityException) PdfError.PasswordProtected else PdfError.Damaged, failure)
    }

    private inner class Session(
        private val rasterizer: PdfPageRasterizer,
        private val recognizer: TextRecognizer,
    ) : PageTextRecognizer.Session {
        override fun recognize(pageIndex: Int): PageLayout {
            val image = draw(pageIndex) ?: return PageLayout(pageIndex, 0f, 0f, emptyList())
            val blocks = read(image).toBlocks()
            return OcrPageLayout.build(pageIndex, image.width, image.height, image.pixelsPerPoint, blocks)
        }

        override fun close() {
            recognizer.close()
            rasterizer.close()
        }

        /** The page's picture, or null when the page cannot be drawn: one broken page does not lose the book. */
        private fun draw(pageIndex: Int): PageImage? =
            try {
                rasterizer.render(pageIndex)
            } catch (e: OutOfMemoryError) {
                throw OcrException(PdfError.LimitExceeded(PdfLimit.MEMORY), e)
            } catch (e: IllegalStateException) {
                Diagnostics.recordHandled("draw PDF page", e)
                null
            } catch (e: IllegalArgumentException) {
                Diagnostics.recordHandled("draw PDF page", e)
                null
            }

        /** What ML Kit reads on [image]; the bitmap is recycled only once ML Kit's task is over. */
        private fun read(image: PageImage): Text {
            var taskOver = false
            try {
                val task = recognizer.process(InputImage.fromBitmap(image.bitmap, NO_ROTATION))
                return Tasks.await(task, limits.pageTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS).also {
                    taskOver = true
                }
            } catch (e: ExecutionException) {
                taskOver = true
                throw OcrException(PdfError.TextRecognitionFailed, e.cause ?: e)
            } catch (e: TimeoutException) {
                throw OcrException(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET), e)
            } finally {
                if (taskOver) image.bitmap.recycle()
            }
        }
    }

    private companion object {
        const val NO_ROTATION = 0
    }
}

/** The blocks and lines ML Kit found, with their boxes in the picture's pixels; lines without a box are left out. */
internal fun Text.toBlocks(): List<RecognizedBlock> =
    textBlocks.map { block ->
        RecognizedBlock(
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { box ->
                    RecognizedLine(
                        line.text,
                        box.left.toFloat(),
                        box.top.toFloat(),
                        box.right.toFloat(),
                        box.bottom.toFloat(),
                    )
                }
            },
        )
    }
