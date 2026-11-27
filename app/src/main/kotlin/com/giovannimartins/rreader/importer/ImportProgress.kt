package com.giovannimartins.rreader.importer

/** How far a running import got. Only stages that take long report progress; the others are just "preparing". */
sealed interface ImportProgress {
    /** Text recognition of a scanned PDF is reading the [page]-th of the [pageCount] pages it has to read. */
    data class RecognizingText(
        val page: Int,
        val pageCount: Int,
    ) : ImportProgress {
        init {
            require(page in 1..pageCount) { "page must be between 1 and pageCount" }
        }
    }
}

/** Hears the progress of an import, on the thread that parses the book. */
fun interface ImportProgressListener {
    fun onProgress(progress: ImportProgress)

    companion object {
        /** Ignores progress. */
        val NONE: ImportProgressListener = ImportProgressListener {}
    }
}
