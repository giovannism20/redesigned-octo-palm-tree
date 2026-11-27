package com.giovannimartins.rreader.textkit.epub

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Keeps one parse within its time limit ([ArchiveLimits.maxParseTime]) and text limit ([ArchiveLimits.maxTextChars],
 * SR-2), and lets the interruption of its thread stop it, as when the import that runs it is cancelled. Parsing code
 * calls [check] before every spine document, for every element it walks and in the loops that assemble chapters, so no
 * input keeps a parse busy for long between two checks, and counts the text it extracts with [countText].
 */
internal class ParseBudget(
    private val maxTime: Duration,
    private val maxTextChars: Long = Long.MAX_VALUE,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val start = timeSource.markNow()
    private var textChars = 0L

    /**
     * Throws [InterruptedException] when the thread was interrupted, and aborts the parse with [EpubError.TooComplex]
     * once [maxTime] has passed.
     */
    fun check() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("EPUB parse cancelled")
        if (start.elapsedNow() > maxTime) abortParse(EpubError.TooComplex)
    }

    /** Counts [chars] more characters of extracted text, aborting the parse once they exceed [maxTextChars]. */
    fun countText(chars: Int) {
        textChars += chars
        if (textChars > maxTextChars) abortParse(EpubError.LimitExceeded(ArchiveLimit.MAX_TEXT_CHARS))
    }

    companion object {
        /** A budget without limits, for small inputs that are not books, such as metadata fragments. */
        fun unlimited(): ParseBudget = ParseBudget(Duration.INFINITE)
    }
}
