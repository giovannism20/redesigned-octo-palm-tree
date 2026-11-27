package com.giovannimartins.rreader.pdf

import kotlin.time.TimeSource

/**
 * Keeps one extraction within the time and size [limits] and lets it be cancelled. PdfBox has no cancellation of its
 * own, so the extractor calls [checkPage] and [countGlyph] from its callbacks; they interrupt the extraction by
 * throwing.
 */
internal class ExtractionBudget(
    private val limits: PdfLimits,
    timeSource: TimeSource,
) {
    private val start = timeSource.markNow()
    private var glyphs = 0L

    /**
     * Throws [InterruptedException] when the import was cancelled (its thread interrupted, as `runInterruptible` does
     * when the coroutine is cancelled), or [ExtractionStopped] when the time budget is spent.
     */
    fun checkPage() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("PDF extraction cancelled")
        if (start.elapsedNow() > limits.timeBudget) throw ExtractionStopped(PdfLimit.TIME_BUDGET)
    }

    /** Counts one glyph, enforcing [PdfLimits.maxGlyphs] and checking time and cancellation now and then. */
    fun countGlyph() {
        glyphs++
        if (glyphs > limits.maxGlyphs) throw ExtractionStopped(PdfLimit.MAX_GLYPHS)
        if (glyphs % CHECK_INTERVAL == 0L) checkPage()
    }

    companion object {
        /** Glyphs between two checks of the clock. */
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
