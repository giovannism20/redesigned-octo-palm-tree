package com.giovannimartins.rreader.textkit.epub

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Keeps one parse within its time limit ([ArchiveLimits.maxParseTime], SR-2) and lets the interruption of its thread
 * stop it, as when the import that runs it is cancelled. Parsing code calls [check] before every spine document, for
 * every element it walks and in the loops that assemble chapters, so no input keeps a parse busy for long between two
 * checks.
 */
internal class ParseBudget(
    private val maxTime: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val start = timeSource.markNow()

    /**
     * Throws [InterruptedException] when the thread was interrupted, and aborts the parse with [EpubError.TooComplex]
     * once [maxTime] has passed.
     */
    fun check() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("EPUB parse cancelled")
        if (start.elapsedNow() > maxTime) abortParse(EpubError.TooComplex)
    }

    companion object {
        /** A budget without a time limit, for small inputs that are not books, such as metadata fragments. */
        val UNLIMITED: ParseBudget = ParseBudget(Duration.INFINITE)
    }
}
