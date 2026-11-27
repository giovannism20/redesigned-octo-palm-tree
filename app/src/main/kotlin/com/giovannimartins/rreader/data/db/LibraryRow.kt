package com.giovannimartins.rreader.data.db

import androidx.room.ColumnInfo
import com.giovannimartins.rreader.textkit.model.MetadataLimits

/**
 * A book as the library lists it, with how many of its characters were already read: only the columns the library
 * shows, its title and authors cut to their limits (see [BookDao.observeLibrary]).
 */
data class LibraryRow(
    val id: String,
    val title: String,
    val authors: List<String>,
    val status: BookStatus,
    @ColumnInfo(name = "failure_reason") val failureReason: ImportFailure?,
    @ColumnInfo(name = "total_chars") val totalChars: Long,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    /** The book's own voice, or null when it reads with the default voice. */
    @ColumnInfo(name = "voice_id") val voiceId: String?,
    @ColumnInfo(name = "chars_read") val charsRead: Long,
) {
    companion object {
        /** The longest authors column read: every author at the name limit, with the separators between them. */
        const val MAX_AUTHORS_COLUMN_LENGTH: Int = MetadataLimits.MAX_AUTHORS * (MetadataLimits.MAX_AUTHOR_LENGTH + 1)
    }
}
