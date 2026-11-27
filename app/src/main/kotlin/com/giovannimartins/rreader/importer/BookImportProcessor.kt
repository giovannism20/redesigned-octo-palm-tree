package com.giovannimartins.rreader.importer

import android.database.SQLException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Second half of an import: parses a stored source file and persists the
 * normalized chapters, cover and metadata. Runs inside [ImportBookWorker] and
 * is safe to repeat for the same book: a run stopped by the system resumes
 * from the pages text recognition stored, and skips a native call that made
 * the process die twice (`NativeCallMarkers`); both are deleted once the
 * import is over, ready or failed. When the book was removed meanwhile, whatever the
 * import wrote is deleted as it ends, also when it is cancelled. Anything a
 * parser or the storage throws, cancellation aside, ends the import as failed,
 * so a book never stays "being prepared" because of it.
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
        /**
         * Imports [bookId], telling [progress] how long stages advance; failures are recorded on the book row rather
         * than thrown.
         */
        suspend fun process(
            bookId: String,
            progress: ImportProgressListener = ImportProgressListener.NONE,
        ) {
            try {
                import(bookId, progress)
            } finally {
                // The book may have been removed while its files were being written, which recreates its folder.
                withContext(NonCancellable + io) { if (bookDao.findById(bookId) == null) files.delete(bookId) }
            }
        }

        /**
         * Gives up on the import of [bookId] when it was started too many times without ending, because the system
         * kept stopping it or its process kept dying in it (see [ImportBookWorker.MAX_RUN_ATTEMPTS]): a book still
         * being prepared is marked as too complex for the phone, and the files kept to resume the import are deleted.
         */
        suspend fun abandon(bookId: String) =
            withContext(NonCancellable + io) {
                val book = bookDao.findById(bookId)
                if (book?.status == BookStatus.IMPORTING) bookDao.markFailed(bookId, ImportFailure.TOO_COMPLEX)
                if (book == null) files.delete(bookId) else files.deleteResumeFiles(bookId)
            }

        private suspend fun import(
            bookId: String,
            progress: ImportProgressListener,
        ) {
            val book = withContext(io) { bookDao.findById(bookId) }
            if (book == null || book.status == BookStatus.READY) return
            val format = BookFormat.entries.firstOrNull { it.name == book.format }
            val parser = format?.let(parsers::get)
            val failure =
                if (format == null || parser == null) {
                    ImportFailure.FORMAT_NOT_YET_SUPPORTED
                } else {
                    val request = ParseRequest(bookId, files.sourceFile(bookId, format), book.title, progress)
                    parseAndPersist(book, parser, request)
                }
            failure?.let { fail(bookId, it) }
            withContext(io) { files.deleteResumeFiles(bookId) }
        }

        /**
         * Parses [request] and stores the book; returns why the import failed, or null when the book is ready.
         * Whatever escapes the parser or the storage is a failure too, cancellation aside: running out of memory or
         * stack means the book is too complex for the phone, anything else that it could not be read.
         */
        @Suppress("TooGenericExceptionCaught") // Nothing may leave the book "being prepared" forever.
        private suspend fun parseAndPersist(
            book: BookEntity,
            parser: BookParser,
            request: ParseRequest,
        ): ImportFailure? =
            try {
                // Parsing blocks; a cancelled import interrupts it, and parsers that check for interruption stop.
                when (val outcome = runInterruptible(cpu) { parser.parse(request) }) {
                    is ParseOutcome.Failed -> outcome.reason
                    is ParseOutcome.Parsed -> persist(book, outcome.book)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Diagnostics.recordHandled("import book", e)
                if (e is VirtualMachineError) ImportFailure.TOO_COMPLEX else ImportFailure.CORRUPTED
            }

        /**
         * Stores [parsed] as [book]; returns [ImportFailure.STORAGE_ERROR] when its files or its rows cannot be
         * written, for example on a full disk.
         */
        private suspend fun persist(
            book: BookEntity,
            parsed: NormalizedBook,
        ): ImportFailure? =
            withContext(io) {
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
                    null
                } catch (e: IOException) {
                    storageFailure(e)
                } catch (e: SQLException) {
                    storageFailure(e)
                }
            }

        private fun storageFailure(error: Exception): ImportFailure {
            Diagnostics.recordHandled("persist import", error)
            return ImportFailure.STORAGE_ERROR
        }

        private suspend fun fail(
            bookId: String,
            reason: ImportFailure,
        ) = withContext(io) { bookDao.markFailed(bookId, reason) }
    }
