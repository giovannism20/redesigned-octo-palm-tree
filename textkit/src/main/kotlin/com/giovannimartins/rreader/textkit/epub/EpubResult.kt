package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.NormalizedBook

/** Outcome of [EpubParser.parse]: a book, or the reason there is none. */
public sealed interface EpubResult {
    /** The file was a readable EPUB and became [book]. */
    public data class Success(
        val book: NormalizedBook,
    ) : EpubResult

    /** The file could not become a book; [error] says why. */
    public data class Failure(
        val error: EpubError,
    ) : EpubResult
}
