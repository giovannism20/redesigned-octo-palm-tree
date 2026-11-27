package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.pdf.FirstPageCoverRenderer
import com.giovannimartins.rreader.pdf.PdfContent
import com.giovannimartins.rreader.pdf.PdfCoverRenderer
import com.giovannimartins.rreader.pdf.PdfError
import com.giovannimartins.rreader.pdf.PdfExtraction
import com.giovannimartins.rreader.pdf.PdfLayoutExtractor
import com.giovannimartins.rreader.pdf.PdfLimit
import com.giovannimartins.rreader.pdf.PdfOcr
import com.giovannimartins.rreader.textkit.layout.DocumentInfoCleaner
import com.giovannimartins.rreader.textkit.layout.LayoutBookBuilder
import com.giovannimartins.rreader.textkit.layout.LayoutError
import com.giovannimartins.rreader.textkit.layout.LayoutResult
import com.giovannimartins.rreader.textkit.model.BookMetadata
import java.io.File
import javax.inject.Inject

/**
 * Imports PDFs: [PdfLayoutExtractor] reads the positioned lines of the text layer, [PdfOcr] reads the scanned pages
 * with on-device text recognition (reporting its progress), the `:textkit` [LayoutBookBuilder] cleans all pages into
 * chapters, and the first page becomes the cover. The document's own title, authors and language are used when
 * [DocumentInfoCleaner] finds them meaningful; the title otherwise falls back to the file name. Throws only
 * [InterruptedException], when the import is cancelled.
 */
class PdfBookParser internal constructor(
    private val extractor: PdfLayoutExtractor,
    private val ocr: PdfOcr,
    private val coverRenderer: PdfCoverRenderer,
    private val builder: LayoutBookBuilder,
) : BookParser {
    @Inject
    constructor(
        extractor: PdfLayoutExtractor,
        ocr: PdfOcr,
        coverRenderer: FirstPageCoverRenderer,
    ) : this(extractor, ocr, coverRenderer, LayoutBookBuilder())

    override fun parse(request: ParseRequest): ParseOutcome =
        when (val extraction = read(request)) {
            is PdfExtraction.Failed -> ParseOutcome.Failed(extraction.error.toImportFailure())
            is PdfExtraction.Extracted -> build(extraction.content, request.source, request.fallbackTitle)
        }

    /** The text layer of every page, with the scanned pages read by text recognition. */
    private fun read(request: ParseRequest): PdfExtraction =
        when (val extraction = extractor.extract(request.source)) {
            is PdfExtraction.Failed -> {
                extraction
            }

            is PdfExtraction.Extracted -> {
                ocr.recognize(request.bookId, request.source, extraction.content) { page, pageCount ->
                    request.progress.onProgress(ImportProgress.RecognizingText(page, pageCount))
                }
            }
        }

    private fun build(
        content: PdfContent,
        source: File,
        fallbackTitle: String,
    ): ParseOutcome {
        val metadata =
            BookMetadata(
                title = DocumentInfoCleaner.title(content.info.title) ?: fallbackTitle,
                authors = DocumentInfoCleaner.authors(content.info.author),
                language = DocumentInfoCleaner.language(content.info.language),
            )
        return when (val result = builder.build(metadata, content.pages, content.outline)) {
            is LayoutResult.Success -> ParseOutcome.Parsed(result.book.copy(cover = coverRenderer.render(source)))
            is LayoutResult.Failure -> ParseOutcome.Failed(result.error.toImportFailure())
        }
    }
}

/** How each PDF reading problem is explained to the user. */
internal fun PdfError.toImportFailure(): ImportFailure =
    when (this) {
        PdfError.Damaged -> ImportFailure.CORRUPTED
        PdfError.PasswordProtected -> ImportFailure.PASSWORD_PROTECTED
        PdfError.TextRecognitionFailed -> ImportFailure.OCR_FAILED
        is PdfError.LimitExceeded -> limit.toImportFailure()
    }

private fun PdfLimit.toImportFailure(): ImportFailure =
    when (this) {
        PdfLimit.MAX_PAGES -> ImportFailure.TOO_MANY_PAGES
        PdfLimit.MAX_OCR_PAGES -> ImportFailure.TOO_MANY_SCANNED_PAGES
        PdfLimit.TIME_BUDGET, PdfLimit.MAX_GLYPHS, PdfLimit.MEMORY -> ImportFailure.TOO_COMPLEX
    }

/** How each layout problem is explained to the user. */
internal fun LayoutError.toImportFailure(): ImportFailure =
    when (this) {
        LayoutError.ScannedDocument -> ImportFailure.SCANNED_PDF
        LayoutError.NoReadableText -> ImportFailure.NO_READABLE_TEXT
    }
