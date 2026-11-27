package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.textkit.layout.OutlineEntry
import com.giovannimartins.rreader.textkit.layout.PageLayout

/** Outcome of [PdfLayoutExtractor.extract]: what the PDF contains, or why it could not be read. */
sealed interface PdfExtraction {
    data class Extracted(
        val content: PdfContent,
    ) : PdfExtraction

    data class Failed(
        val error: PdfError,
    ) : PdfExtraction
}

/** The raw material of a book: every page's lines, the outline and what the document says about itself. */
data class PdfContent(
    /** One entry per page, in document order, indexed from 0. */
    val pages: List<PageLayout>,
    val outline: List<OutlineEntry>,
    val info: PdfInfo,
    /**
     * Indexes of the pages whose text may be a picture: they draw an image or many path segments, or could not be read
     * completely. Text recognition looks at those among the pages without a usable text layer.
     */
    val picturedPages: Set<Int> = emptySet(),
)

/** The document information dictionary and catalog language, as stored: possibly blank or meaningless. */
data class PdfInfo(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
)

/** Why a PDF could not be read, from its text layer or by text recognition. Each case maps to one message. */
sealed interface PdfError {
    /** The file is not a readable PDF: damaged, truncated, or without pages. */
    data object Damaged : PdfError

    /** The on-device text recognizer failed on a scanned page; trying again may work. */
    data object TextRecognitionFailed : PdfError

    /** Opening the document needs a password. */
    data object PasswordProtected : PdfError

    /** The platform's PDF renderer made the app close twice in a row when opening the file (a native crash). */
    data object RendererCrashed : PdfError

    /** The document exceeds [limit]. */
    data class LimitExceeded(
        val limit: PdfLimit,
    ) : PdfError
}
