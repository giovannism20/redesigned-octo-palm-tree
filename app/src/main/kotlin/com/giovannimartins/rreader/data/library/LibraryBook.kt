package com.giovannimartins.rreader.data.library

import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.importer.ImportProgress
import java.io.File

/** A book as the library screen shows it. */
data class LibraryBook(
    val id: String,
    /** Empty when unknown; the UI shows a localized "Untitled". */
    val title: String,
    val authors: List<String>,
    val status: BookStatus,
    val failure: ImportFailure?,
    val coverFile: File?,
    val charsRead: Long,
    val totalChars: Long,
    /** The book's own voice, or null when it reads with the default voice. */
    val voiceId: String? = null,
    /** How far the import of a book being prepared got, when its current stage reports it. */
    val importProgress: ImportProgress? = null,
) {
    /** Fraction of the book already listened to, between 0 and 1. */
    val progress: Float
        get() = if (totalChars <= 0) 0f else (charsRead.toDouble() / totalChars).coerceIn(0.0, 1.0).toFloat()

    /** Whether listening ever started. */
    val started: Boolean get() = charsRead > 0

    /** Whether the book is ready, started and not finished: one the listener is in the middle of. */
    val inProgress: Boolean get() = status == BookStatus.READY && started && charsRead < totalChars
}
