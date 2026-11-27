package com.giovannimartins.rreader.pdf

import android.content.Context
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.layout.OutlineEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.Writer
import javax.inject.Inject
import kotlin.time.TimeSource

/**
 * Reads the text layer of a PDF with PdfBox-Android: every page's lines with their position and font size, the
 * outline, and the document information title, author and language (ADR 0005).
 *
 * The file is untrusted input (SR-4). It is opened without a password, so documents that need one are refused, and
 * with PdfBox's buffers capped at [PdfLimits.mainMemoryBytes] of memory, the rest going to a temporary file in the
 * app cache, up to [PdfLimits.maxBufferBytes]. Documents over [PdfLimits.maxPages] pages are refused before any text
 * is read; extraction stops at [PdfLimits.timeBudget] or [PdfLimits.maxGlyphs] glyphs, and ignores the glyphs of a
 * page beyond [PdfLimits.maxGlyphsPerPage]. A document with no readable page is damaged. Running out of memory, or of
 * stack on absurdly nested content, is reported
 * instead of crashing the app, which would make WorkManager retry the same import forever.
 *
 * [extract] never throws for bad input. It throws [InterruptedException] when its thread is interrupted, which is how
 * a cancelled import (see `runInterruptible`) stops it. Never logs text or file names (SR-8).
 */
class PdfLayoutExtractor internal constructor(
    private val context: Context,
    private val limits: PdfLimits,
    private val timeSource: TimeSource,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context, PdfLimits(), TimeSource.Monotonic)

    @Suppress("TooGenericExceptionCaught") // A parser of hostile files may fail in any way; that is a damaged file.
    fun extract(file: File): PdfExtraction {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context)
        val budget = ExtractionBudget(limits, timeSource)
        return try {
            PDDocument.load(file, NO_PASSWORD, memoryUsage()).use { document -> read(document, budget) }
        } catch (e: InvalidPasswordException) {
            failure(PdfError.PasswordProtected, e)
        } catch (e: IOException) {
            failure(if (e.isBufferLimit()) PdfError.LimitExceeded(PdfLimit.MEMORY) else PdfError.Damaged, e)
        } catch (e: ExtractionStopped) {
            failure(PdfError.LimitExceeded(e.limit), e)
        } catch (e: RuntimeException) {
            failure(PdfError.Damaged, e)
        } catch (e: OutOfMemoryError) {
            failure(PdfError.LimitExceeded(PdfLimit.MEMORY), e)
        } catch (e: StackOverflowError) {
            failure(PdfError.Damaged, e)
        }
    }

    private fun read(
        document: PDDocument,
        budget: ExtractionBudget,
    ): PdfExtraction {
        val pageCount = document.numberOfPages
        return when {
            pageCount == 0 -> {
                PdfExtraction.Failed(PdfError.Damaged)
            }

            pageCount > limits.maxPages -> {
                PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.MAX_PAGES))
            }

            else -> {
                val stripper = LayoutTextStripper(budget, limits.maxGlyphsPerPage)
                stripper.writeText(document, DiscardingWriter)
                if (stripper.pages.isEmpty()) PdfExtraction.Failed(PdfError.Damaged) else extracted(document, stripper)
            }
        }
    }

    private fun extracted(
        document: PDDocument,
        stripper: LayoutTextStripper,
    ): PdfExtraction {
        val information = document.documentInformation
        return PdfExtraction.Extracted(
            PdfContent(
                pages = stripper.pages,
                outline = outlineOf(document),
                info = PdfInfo(information.title, information.author, document.documentCatalog.language),
                picturedPages = stripper.picturedPages,
            ),
        )
    }

    /** The outline, or none when it is too damaged to read: the text alone still makes a book. */
    @Suppress("TooGenericExceptionCaught") // Same reason as in extract.
    private fun outlineOf(document: PDDocument): List<OutlineEntry> =
        try {
            PdfOutlineReader.read(document)
        } catch (e: IOException) {
            Diagnostics.recordHandled("read PDF outline", e)
            emptyList()
        } catch (e: RuntimeException) {
            Diagnostics.recordHandled("read PDF outline", e)
            emptyList()
        }

    private fun memoryUsage(): MemoryUsageSetting =
        MemoryUsageSetting
            .setupMixed(limits.mainMemoryBytes, limits.maxBufferBytes)
            .setTempDir(File(context.cacheDir, TEMP_DIRECTORY).apply { mkdirs() })

    private fun failure(
        error: PdfError,
        cause: Throwable,
    ): PdfExtraction {
        Diagnostics.recordHandled("extract PDF", cause)
        return PdfExtraction.Failed(error)
    }

    /** PDFTextStripper writes plain text as it goes; the layout is collected instead, so that text is dropped. */
    private object DiscardingWriter : Writer() {
        override fun write(
            buffer: CharArray,
            offset: Int,
            length: Int,
        ) = Unit

        override fun flush() = Unit

        override fun close() = Unit
    }

    private companion object {
        const val NO_PASSWORD = ""
        const val TEMP_DIRECTORY = "pdfbox"
    }
}
