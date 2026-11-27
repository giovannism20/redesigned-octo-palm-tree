package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import java.io.File

/** Turns a stored source file into a [NormalizedBook]. Implementations never throw for bad input. */
fun interface BookParser {
    /** Parses [request]; throws only [InterruptedException], when the import is cancelled. */
    fun parse(request: ParseRequest): ParseOutcome
}

/** One book to parse: its id, its stored [source], the title to use when the book has none, and its progress sink. */
data class ParseRequest(
    val bookId: String,
    val source: File,
    val fallbackTitle: String,
    val progress: ImportProgressListener = ImportProgressListener.NONE,
)

/** Result of [BookParser.parse]. */
sealed interface ParseOutcome {
    data class Parsed(
        val book: NormalizedBook,
    ) : ParseOutcome

    data class Failed(
        val reason: ImportFailure,
    ) : ParseOutcome
}
