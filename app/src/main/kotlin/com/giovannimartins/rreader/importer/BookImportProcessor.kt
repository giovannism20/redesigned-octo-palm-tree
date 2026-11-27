package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ChapterEntity
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.DefaultDispatcher
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Second half of an import: parses a stored source file and persists the
 * normalized chapters, cover and metadata. Runs inside [ImportBookWorker] and
 * is safe to repeat for the same book.
 */
class BookImportProcessor
    @Inject
    constructor(
        private val bookDao: BookDao,
        private val files: BookFileStore,
        private val parsers: Map<BookFormat, @JvmSuppressWildcards BookParser>,
        @IoDispatcher private val io: CoroutineDispatcher,
        @DefaultDispatcher private val cpu: CoroutineDispatcher,
    ) {
        /** Imports [bookId]; failures are recorded on the book row rather than thrown. */
        suspend fun process(bookId: String) {
            val book = withContext(io) { bookDao.findById(bookId) }
            if (book == null || book.status == BookStatus.READY) return
            val format = BookFormat.entries.firstOrNull { it.name == book.format }
            val parser = format?.let(parsers::get)
            if (format == null || parser == null) {
                fail(bookId, ImportFailure.FORMAT_NOT_YET_SUPPORTED)
            } else {
                val source = files.sourceFile(bookId, format)
                // Parsing blocks; a cancelled import interrupts it, and parsers that check for interruption stop.
                when (val outcome = runInterruptible(cpu) { parser.parse(source, book.title) }) {
                    is ParseOutcome.Failed -> fail(bookId, outcome.reason)
                    is ParseOutcome.Parsed -> persist(book, outcome.book)
                }
            }
        }

        private suspend fun persist(
            book: BookEntity,
            parsed: NormalizedBook,
        ) = withContext(io) {
            try {
                files.writeChapters(book.id, parsed.chapters)
                parsed.cover?.let { files.writeCover(book.id, it) }
                val chapters =
                    parsed.chapters.mapIndexed { index, chapter ->
                        ChapterEntity(book.id, index, chapter.title, chapter.text.length)
                    }
                val metadata = parsed.metadata
                bookDao.completeImport(
                    book.copy(
                        title = metadata.title.ifBlank { book.title },
                        authors = metadata.authors,
                        language = metadata.language,
                        description = metadata.description,
                        totalChars = parsed.totalLength,
                        hasCover = parsed.cover != null,
                    ),
                    chapters,
                )
            } catch (e: IOException) {
                Diagnostics.recordHandled("persist import", e)
                fail(book.id, ImportFailure.STORAGE_ERROR)
            }
        }

        private suspend fun fail(
            bookId: String,
            reason: ImportFailure,
        ) = withContext(io) { bookDao.markFailed(bookId, reason) }
    }
