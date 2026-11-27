package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.pdf.FirstPageCoverRenderer
import com.giovannimartins.rreader.pdf.NativeCall
import com.giovannimartins.rreader.pdf.NativeCallGuard
import com.giovannimartins.rreader.pdf.NativeCallMarkers
import com.giovannimartins.rreader.pdf.NativeStage
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
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import java.io.File
import javax.inject.Inject

/**
 * Imports PDFs: [PdfLayoutExtractor] reads the positioned lines of the text layer, [PdfOcr] reads the scanned pages
 * with on-device text recognition (reporting its progress), the `:textkit` [LayoutBookBuilder] cleans all pages into
 * chapters, and the first page becomes the cover. The document's own title, authors and language are used when
 * [DocumentInfoCleaner] finds them meaningful; the title otherwise falls back to the file name. Calls into native code
 * (the platform renderer, ML Kit) go through the run's [NativeCallGuard], so a cover in which the process died twice
 * in a row is left out. Throws only [InterruptedException], when the import is cancelled.
 */
class PdfBookParser internal constructor(
    private val extractor: PdfLayoutExtractor,
    private val ocr: PdfOcr,
    private val coverRenderer: PdfCoverRenderer,
    private val builder: LayoutBookBuilder,
    private val nativeCalls: NativeCallMarkers,
) : BookParser {
    @Inject
    constructor(
        extractor: PdfLayoutExtractor,
        ocr: PdfOcr,
        coverRenderer: FirstPageCoverRenderer,
        nativeCalls: NativeCallMarkers,
    ) : this(extractor, ocr, coverRenderer, LayoutBookBuilder(), nativeCalls)

    override fun parse(request: ParseRequest): ParseOutcome {
        val guard = nativeCalls.forRun(request.bookId)
        return when (val extraction = read(request, guard)) {
            is PdfExtraction.Failed -> ParseOutcome.Failed(extraction.error.toImportFailure())
            is PdfExtraction.Extracted -> build(extraction.content, request, guard)
        }
    }

    /** The text layer of every page, with the scanned pages read by text recognition. */
    private fun read(
        request: ParseRequest,
        guard: NativeCallGuard,
    ): PdfExtraction =
        when (val extraction = extractor.extract(request.source)) {
            is PdfExtraction.Failed -> {
                extraction
            }

            is PdfExtraction.Extracted -> {
                ocr.recognize(request.bookId, request.source, extraction.content, guard) { page, pageCount ->
                    request.progress.onProgress(ImportProgress.RecognizingText(page, pageCount))
                }
            }
        }

    /** The first page as the cover, unless drawing it made the process die twice in a row. */
    private fun cover(
        source: File,
        guard: NativeCallGuard,
    ): CoverImage? {
        val call = NativeCall(NativeStage.COVER)
        return if (guard.shouldSkip(call)) null else guard.run(call) { coverRenderer.render(source) }
    }

    private fun build(
        content: PdfContent,
        request: ParseRequest,
        guard: NativeCallGuard,
    ): ParseOutcome {
        val metadata =
            BookMetadata(
                title = MetadataLimits.title(DocumentInfoCleaner.title(content.info.title) ?: request.fallbackTitle),
                authors = DocumentInfoCleaner.authors(content.info.author),
                language = DocumentInfoCleaner.language(content.info.language),
            )
        return when (val result = builder.build(metadata, content.pages, content.outline)) {
            is LayoutResult.Success -> ParseOutcome.Parsed(result.book.copy(cover = cover(request.source, guard)))
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
        PdfError.RendererCrashed -> ImportFailure.TOO_COMPLEX
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
