package com.giovannimartins.rreader.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A book joined with how many of its characters were already read. */
data class LibraryRow(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = "chars_read") val charsRead: Long,
    @ColumnInfo(name = "position_updated_at") val positionUpdatedAt: Long?,
)
