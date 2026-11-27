package com.giovannimartins.rreader.textkit.epub

/**
 * Stops a parse with a typed [error]. It never leaves this package: the parser's entry point turns it into a failed
 * result, so callers only ever see [EpubError] values.
 */
internal class EpubParseException(
    val error: EpubError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

/** Aborts the current parse with [error], keeping [cause] for debugging. */
internal fun abortParse(
    error: EpubError,
    cause: Throwable? = null,
): Nothing = throw EpubParseException(error, cause)
