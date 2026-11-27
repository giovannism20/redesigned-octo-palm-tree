package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import java.io.File

/** Turns a stored source file into a [NormalizedBook]. Implementations never throw for bad input. */
fun interface BookParser {
    fun parse(
        source: File,
        fallbackTitle: String,
    ): ParseOutcome
}

/** Result of [BookParser.parse]. */
sealed interface ParseOutcome {
    data class Parsed(
        val book: NormalizedBook,
    ) : ParseOutcome

    data class Failed(
        val reason: ImportFailure,
    ) : ParseOutcome
}
