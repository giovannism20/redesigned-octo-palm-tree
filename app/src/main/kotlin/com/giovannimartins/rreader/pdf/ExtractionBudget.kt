package com.giovannimartins.rreader.pdf

import kotlin.time.TimeSource

/**
 * Keeps one extraction within the time and size [limits] and lets it be cancelled. PdfBox has no cancellation of its
 * own, so the extractor reports pages, glyphs and content stream operators from its callbacks; the budget interrupts
 * the extraction by throwing.
 */
internal class ExtractionBudget(
    private val limits: PdfLimits,
    timeSource: TimeSource,
) {
    private val start = timeSource.markNow()
    private var pages = 0
    private var glyphs = 0L
    private var operators = 0L

    /**
     * Throws [InterruptedException] when the import was cancelled (its thread interrupted, as `runInterruptible` does
     * when the coroutine is cancelled), or [ExtractionStopped] when the time budget is spent.
     */
    fun check() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("PDF extraction cancelled")
        if (start.elapsedNow() > limits.timeBudget) throw ExtractionStopped(PdfLimit.TIME_BUDGET)
    }

    /**
     * Counts a page about to be read, enforcing [PdfLimits.maxPages] again: the page count a document declares, which
     * the extractor checks first, can be a lie.
     */
    fun startPage() {
        pages++
        if (pages > limits.maxPages) throw ExtractionStopped(PdfLimit.MAX_PAGES)
        check()
    }

    /** Counts one glyph, kept or not, enforcing [PdfLimits.maxGlyphs] and checking the clock now and then. */
    fun countGlyph() {
        glyphs++
        if (glyphs > limits.maxGlyphs) throw ExtractionStopped(PdfLimit.MAX_GLYPHS)
        if (glyphs % CHECK_INTERVAL == 0L) check()
    }

    /** Counts one content stream operator, so that a page full of drawing and no text still checks the clock. */
    fun countOperator() {
        operators++
        if (operators % CHECK_INTERVAL == 0L) check()
    }

    companion object {
        /** Glyphs, or operators, between two checks of the clock. */
        const val CHECK_INTERVAL: Long = 1_024
    }
}

/**
 * Whether this exception, or one of its causes, is PdfBox refusing to buffer more than [PdfLimits.maxBufferBytes].
 * PdfBox 2.0 reports that limit only as an `IOException` with this message; a test pins the behaviour.
 */
internal fun Throwable.isBufferLimit(): Boolean =
    generateSequence(this) { it.cause }.any { it.message?.contains(BUFFER_LIMIT_MESSAGE) == true }

private const val BUFFER_LIMIT_MESSAGE = "Maximum allowed scratch file memory exceeded"

/**
 * Stops an extraction that went over [limit]. Unchecked on purpose: PdfBox swallows some `IOException`s while it
 * draws form XObjects, and this must reach the extractor.
 */
internal class ExtractionStopped(
    val limit: PdfLimit,
    cause: Throwable? = null,
) : RuntimeException("PDF extraction exceeded $limit", cause)
