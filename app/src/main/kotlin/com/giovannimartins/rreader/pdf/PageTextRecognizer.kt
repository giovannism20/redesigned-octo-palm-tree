package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.textkit.layout.OcrPageLayout
import com.giovannimartins.rreader.textkit.layout.PageLayout
import java.io.Closeable
import java.io.File

/**
 * Reads the text of PDF pages that have no usable text layer (OCR pages), one page at a time. The import pipeline
 * depends on this interface so that it can be tested without the platform renderer and ML Kit;
 * [MlKitPageTextRecognizer] is the implementation the app uses.
 */
fun interface PageTextRecognizer {
    /** Opens [source] for recognition. Throws [OcrException] when the file cannot be drawn or the recognizer fails. */
    fun open(source: File): Session

    /** Recognition of one document, used from one thread; [close] releases the renderer and the recognizer. */
    interface Session : Closeable {
        /**
         * Page [pageIndex] as the recognizer reads it: its size and its lines in PDF points from the top-left corner,
         * as [OcrPageLayout] builds them. A page that cannot be drawn has no lines. Throws [OcrException] when
         * recognition fails or exceeds a limit, and [InterruptedException] when the import is cancelled.
         */
        fun recognize(pageIndex: Int): PageLayout
    }
}

/** Stops the text recognition of a document with [error]. */
class OcrException(
    val error: PdfError,
    cause: Throwable? = null,
) : Exception("Text recognition stopped: $error", cause)
