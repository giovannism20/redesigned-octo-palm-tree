package com.giovannimartins.rreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /**
     * Non-archived books, most recently listened or added first, with characters read so far. Only the columns the
     * library shows are read, and its text columns are cut to their limits, so that every row fits in a cursor
     * window (2 MB) even if a book stored before those limits existed has an enormous title or description (SR-13).
     */
    @Query(
        """
        SELECT b.id,
            substr(b.title, 1, ${MetadataLimits.MAX_TITLE_LENGTH}) AS title,
            substr(b.authors, 1, ${LibraryRow.MAX_AUTHORS_COLUMN_LENGTH}) AS authors,
            b.status, b.failure_reason, b.total_chars, b.has_cover, b.voice_id,
            CASE WHEN p.book_id IS NULL THEN 0 ELSE
                (SELECT COALESCE(SUM(c.char_count), 0) FROM chapters c
                    WHERE c.book_id = b.id AND c.chapter_index < p.chapter_index) + p.char_offset
            END AS chars_read
        FROM books b
        LEFT JOIN reading_positions p ON p.book_id = b.id
        WHERE b.archived = 0
        ORDER BY COALESCE(p.updated_at, b.last_opened_at, b.added_at) DESC
        """,
    )
    fun observeLibrary(): Flow<List<LibraryRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE source_sha256 = :sha256")
    suspend fun findBySourceSha256(sha256: String): BookEntity?

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index")
    suspend fun chapters(bookId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET status = :status, failure_reason = :reason WHERE id = :id")
    suspend fun markFailed(
        id: String,
        reason: ImportFailure,
        status: BookStatus = BookStatus.FAILED,
    )

    @Query("UPDATE books SET status = 'IMPORTING', failure_reason = NULL WHERE id = :id")
    suspend fun markImporting(id: String)

    @Query(
        """
        UPDATE books SET title = :title, authors = :authors, language = :language,
            description = :description, total_chars = :totalChars, chapter_count = :chapterCount,
            has_cover = :hasCover, status = 'READY', failure_reason = NULL
        WHERE id = :id
        """,
    )
    suspend fun markReady(
        id: String,
        title: String,
        authors: String,
        language: String?,
        description: String?,
        totalChars: Long,
        chapterCount: Int,
        hasCover: Boolean,
    )

    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    suspend fun deleteChapters(bookId: String)

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    /** Replaces the chapter list and marks the book ready in one transaction. */
    @Transaction
    suspend fun completeImport(
        book: BookEntity,
        chapters: List<ChapterEntity>,
    ) {
        deleteChapters(book.id)
        insertChapters(chapters)
        markReady(
            id = book.id,
            title = book.title,
            authors = Converters().authorsToColumn(book.authors),
            language = book.language,
            description = book.description,
            totalChars = book.totalChars,
            chapterCount = chapters.size,
            hasCover = book.hasCover,
        )
    }

    @Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(
        id: String,
        at: Long,
    )

    @Query("UPDATE books SET title = :title, authors = :authors WHERE id = :id")
    suspend fun rename(
        id: String,
        title: String,
        authors: String,
    )

    @Query("UPDATE books SET archived = :archived WHERE id = :id")
    suspend fun setArchived(
        id: String,
        archived: Boolean,
    )

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
