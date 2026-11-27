package com.giovannimartins.rreader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/** Chapter metadata. The chapter text is stored in a file named after [index]. */
@Entity(
    tableName = "chapters",
    primaryKeys = ["book_id", "chapter_index"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChapterEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val index: Int,
    val title: String?,
    @ColumnInfo(name = "char_count") val charCount: Int,
)
