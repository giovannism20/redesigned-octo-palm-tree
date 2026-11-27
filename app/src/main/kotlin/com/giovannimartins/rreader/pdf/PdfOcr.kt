package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.giovannimartins.rreader.textkit.layout.ScannedDocumentDetector
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Reads the pages of a PDF that have no usable text layer with a [PageTextRecognizer] and puts what it read in their
 * place, so that the whole book then goes through the same layout cleanup (ADR 0015).
 *
 * A page is recognized when [ScannedDocumentDetector.pagesWithoutText] lists it and its text may be pictured
 * ([PdfContent.picturedPages]); blank pages and short pages of text PDFs keep their text layer. Each page is stored
 * by [OcrPageStore] as soon as it is read, so an import that the system stopped resumes at the first page not stored
 * yet. Within [OcrLimits] (SR-4): a book with more than [OcrLimits.maxPages] pages to recognize is refused before
 * any page is drawn, and recognition stops once it took [OcrLimits.timeBudget], counting the pages stored by earlier
 * runs. Pages are recognized one at a time in the whole app ([OcrGate]). Every call into the native renderer and
 * recognizer goes through the run's [NativeCallGuard]: a page in which the process died twice in a row is stored
 * without text instead of being drawn again, and a file whose opening did so fails with [PdfError.RendererCrashed].
 *
 * Never throws for bad input; throws [InterruptedException] when the import is cancelled. Never logs recognized text
 * or file names (SR-8).
 */
class PdfOcr internal constructor(
    private val recognizer: PageTextRecognizer,
    private val store: OcrPageStore,
    private val gate: OcrGate,
    private val limits: OcrLimits,
    private val timeSource: TimeSource,
) {
    @Inject
    constructor(
        recognizer: MlKitPageTextRecognizer,
        store: OcrPageStore,
        gate: OcrGate,
    ) : this(recognizer, store, gate, OcrLimits(), TimeSource.Monotonic)

    /**
     * [content] of book [bookId], whose file is [source], with the pages that need it read by text recognition, each
     * native call made through [nativeCalls]. Before each page is recognized, [onPage] hears its position among the
     * pages to recognize, from 1, and their number; pages stored by an earlier run count as done.
     */
    fun recognize(
        bookId: String,
        source: File,
        content: PdfContent,
        nativeCalls: NativeCallGuard,
        onPage: (page: Int, pageCount: Int) -> Unit,
    ): PdfExtraction {
        val wanted = ScannedDocumentDetector.pagesWithoutText(content.pages).filter { it in content.picturedPages }
        return when {
            wanted.isEmpty() -> {
                PdfExtraction.Extracted(content)
            }

            wanted.size > limits.maxPages -> {
                PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.MAX_OCR_PAGES))
            }

            else -> {
                val run = Run(bookId, wanted, nativeCalls, onPage)
                val error = run.recognizeMissing(source)
                if (error != null) {
                    PdfExtraction.Failed(error)
                } else {
                    PdfExtraction.Extracted(content.copy(pages = content.pages.map { run.pages[it.index] ?: it }))
                }
            }
        }
    }

    /** Recognition of one book's [wanted] pages, starting from the pages stored by earlier runs. */
    private inner class Run(
        private val bookId: String,
        private val wanted: List<Int>,
        private val nativeCalls: NativeCallGuard,
        private val onPage: (page: Int, pageCount: Int) -> Unit,
    ) {
        /** The pages read so far, by index. */
        val pages = HashMap<Int, PageLayout>()
        private var spent = Duration.ZERO

        init {
            wanted.forEach { index ->
                store.read(bookId, index)?.let { stored ->
                    pages[index] = stored.page.copy(index = index)
                    spent += stored.elapsed
                }
            }
        }

        /** Recognizes the pages not read yet; returns why recognition stopped, or null when every page was read. */
        fun recognizeMissing(source: File): PdfError? {
            val missing = wanted.filterNot(pages::containsKey)
            val open = NativeCall(NativeStage.OCR_OPEN)
            return when {
                missing.isEmpty() -> null
                nativeCalls.shouldSkip(open) -> PdfError.RendererCrashed
                else -> recognizeAll(source, open, missing)
            }
        }

        @Suppress("TooGenericExceptionCaught") // The renderer and the recognizer read untrusted files natively.
        private fun recognizeAll(
            source: File,
            open: NativeCall,
            missing: List<Int>,
        ): PdfError? =
            try {
                nativeCalls.run(open) { recognizer.open(source) }.use { session ->
                    missing.forEach { index -> recognize(session, index) }
                }
                null
            } catch (e: OcrException) {
                stopped(e, e.error)
            } catch (e: RuntimeException) {
                stopped(e, PdfError.TextRecognitionFailed)
            } catch (e: OutOfMemoryError) {
                stopped(e, PdfError.LimitExceeded(PdfLimit.MEMORY))
            }

        private fun stopped(
            cause: Throwable,
            error: PdfError,
        ): PdfError {
            Diagnostics.recordHandled("recognize PDF pages", cause)
            return error
        }

        private fun recognize(
            session: PageTextRecognizer.Session,
            index: Int,
        ) = gate.withPermit {
            if (spent >= limits.timeBudget) throw OcrException(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET))
            onPage(pages.size + 1, wanted.size)
            val start = timeSource.markNow()
            val call = NativeCall(NativeStage.OCR_PAGE, index)
            val page =
                if (nativeCalls.shouldSkip(call)) {
                    // The process died twice drawing or reading this page: it is kept without text.
                    PageLayout(index, 0f, 0f, emptyList())
                } else {
                    nativeCalls.run(call) { session.recognize(index) }.copy(index = index)
                }
            val elapsed = start.elapsedNow()
            store.write(bookId, page, elapsed)
            pages[index] = page
            spent += elapsed
        }
    }
}
