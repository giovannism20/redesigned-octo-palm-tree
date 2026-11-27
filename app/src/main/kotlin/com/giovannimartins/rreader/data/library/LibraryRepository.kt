package com.giovannimartins.rreader.data.library

import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.Converters
import com.giovannimartins.rreader.data.db.LibraryRow
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.importer.ImportJobs
import com.giovannimartins.rreader.importer.ImportProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Read and manage the books in the library. */
@Singleton
class LibraryRepository
    @Inject
    constructor(
        private val bookDao: BookDao,
        private val files: BookFileStore,
        private val importJobs: ImportJobs,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** Library contents, most recent activity first, with the progress of the books being prepared. */
        val books: Flow<List<LibraryBook>> =
            combine(bookDao.observeLibrary(), importJobs.progress) { rows, progress ->
                rows.map { row -> toLibraryBook(row, progress[row.id]) }
            }

        suspend fun rename(
            bookId: String,
            title: String,
            authors: List<String>,
        ) = withContext(io) {
            bookDao.rename(bookId, title.trim(), Converters().authorsToColumn(authors.map(String::trim)))
        }

        /** Gives book [bookId] its own voice, or makes it follow the default voice again (null). */
        suspend fun setVoice(
            bookId: String,
            voiceId: String?,
        ) = withContext(io) { bookDao.setVoice(bookId, voiceId) }

        /** Book [bookId]'s own voice; null while it follows the default voice. */
        fun voiceOf(bookId: String): Flow<String?> = bookDao.observeVoice(bookId)

        suspend fun setArchived(
            bookId: String,
            archived: Boolean,
        ) = withContext(io) { bookDao.setArchived(bookId, archived) }

        /**
         * Stops the book's import if it is being prepared, then deletes its row (chapters and position cascade) and
         * its files. An import that writes after this cleans up after itself (see `BookImportProcessor`).
         */
        suspend fun remove(bookId: String) =
            withContext(io) {
                importJobs.cancel(bookId)
                bookDao.delete(bookId)
                files.delete(bookId)
            }

        private fun toLibraryBook(
            row: LibraryRow,
            progress: ImportProgress?,
        ): LibraryBook {
            val cover = files.coverFile(row.id).takeIf { row.hasCover && row.status == BookStatus.READY }
            return LibraryBook(
                id = row.id,
                title = row.title,
                authors = row.authors,
                status = row.status,
                failure = row.failureReason,
                coverFile = cover,
                charsRead = row.charsRead,
                totalChars = row.totalChars,
                voiceId = row.voiceId,
                importProgress = progress.takeIf { row.status == BookStatus.IMPORTING },
            )
        }
    }
