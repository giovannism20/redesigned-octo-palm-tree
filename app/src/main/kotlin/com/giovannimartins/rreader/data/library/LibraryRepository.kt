package com.giovannimartins.rreader.data.library

import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.Converters
import com.giovannimartins.rreader.data.db.LibraryRow
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** Library contents, most recent activity first. */
        val books: Flow<List<LibraryBook>> = bookDao.observeLibrary().map { rows -> rows.map(::toLibraryBook) }

        suspend fun rename(
            bookId: String,
            title: String,
            authors: List<String>,
        ) = withContext(io) {
            bookDao.rename(bookId, title.trim(), Converters().authorsToColumn(authors.map(String::trim)))
        }

        suspend fun setArchived(
            bookId: String,
            archived: Boolean,
        ) = withContext(io) { bookDao.setArchived(bookId, archived) }

        /** Deletes the book's row (chapters and position cascade) and then its files. */
        suspend fun remove(bookId: String) =
            withContext(io) {
                bookDao.delete(bookId)
                files.delete(bookId)
            }

        private fun toLibraryBook(row: LibraryRow): LibraryBook {
            val book = row.book
            val cover = files.coverFile(book.id).takeIf { book.hasCover && book.status == BookStatus.READY }
            return LibraryBook(
                id = book.id,
                title = book.title,
                authors = book.authors,
                status = book.status,
                failure = book.failureReason,
                coverFile = cover,
                charsRead = row.charsRead,
                totalChars = book.totalChars,
            )
        }
    }
