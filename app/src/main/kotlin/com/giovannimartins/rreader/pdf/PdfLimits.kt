package com.giovannimartins.rreader.pdf

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Safety limits for extracting the text of a PDF (security requirement SR-4): a hostile or merely gigantic file must
 * end in a typed failure, never in an endless import or an out-of-memory crash.
 */
data class PdfLimits(
    /** Documents with more pages are refused, before any text is read when they declare it. */
    val maxPages: Int = DEFAULT_MAX_PAGES,
    /**
     * Extraction stops once it has run this long, checked before every page and every [ExtractionBudget.CHECK_INTERVAL]
     * glyphs or content stream operators. It stays well under the ten minutes after which WorkManager stops a worker
     * and retries it.
     */
    val timeBudget: Duration = DEFAULT_TIME_BUDGET,
    /** Glyphs read per page; the rest of a page beyond it is ignored, which bounds the memory a page can take. */
    val maxGlyphsPerPage: Int = DEFAULT_MAX_GLYPHS_PER_PAGE,
    /** Glyphs met in the whole document, also those beyond a page's cap, before extraction stops. */
    val maxGlyphs: Long = DEFAULT_MAX_GLYPHS,
    /** Memory PdfBox may use for the file's streams before it spills them to a temporary file in the app cache. */
    val mainMemoryBytes: Long = DEFAULT_MAIN_MEMORY_BYTES,
    /** Memory and temporary file space PdfBox may use in total; more makes the extraction fail. */
    val maxBufferBytes: Long = DEFAULT_MAX_BUFFER_BYTES,
) {
    init {
        require(maxPages > 0 && maxGlyphsPerPage > 0 && maxGlyphs > 0) { "Limits must be positive" }
        require(mainMemoryBytes in 1..maxBufferBytes) { "Main memory must be positive and within the buffer limit" }
    }

    private companion object {
        const val DEFAULT_MAX_PAGES = 3_000
        val DEFAULT_TIME_BUDGET = 5.minutes
        const val DEFAULT_MAX_GLYPHS_PER_PAGE = 100_000
        const val DEFAULT_MAX_GLYPHS = 20_000_000L
        const val DEFAULT_MAIN_MEMORY_BYTES = 16L * 1024 * 1024
        const val DEFAULT_MAX_BUFFER_BYTES = 512L * 1024 * 1024
    }
}

/** Names the [PdfLimits] or [OcrLimits] value a document exceeded; reported by [PdfError.LimitExceeded]. */
enum class PdfLimit {
    /** [PdfLimits.maxPages]. */
    MAX_PAGES,

    /** [OcrLimits.maxPages]: too many pages to recognize. */
    MAX_OCR_PAGES,

    /** [PdfLimits.timeBudget], [OcrLimits.timeBudget] or [OcrLimits.pageTimeout]. */
    TIME_BUDGET,

    /** [PdfLimits.maxGlyphs]. */
    MAX_GLYPHS,

    /**
     * PdfBox reached [PdfLimits.maxBufferBytes], the app ran out of memory while reading the document, or a page
     * picture for text recognition could not be allocated.
     */
    MEMORY,
}
