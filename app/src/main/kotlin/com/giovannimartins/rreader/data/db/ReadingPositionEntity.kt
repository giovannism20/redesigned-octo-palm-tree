package com.giovannimartins.rreader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** The listener's place in a book (ADR 0006). One row per book that was ever played. */
@Entity(
    tableName = "reading_positions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingPositionEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
