package com.giovannimartins.rreader.importer

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.OpenableColumns
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.BookFormatDetector
import com.giovannimartins.rreader.textkit.FormatDetection
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of handing a file to [BookIntake]. */
sealed interface IntakeResult {
    data class Queued(
        val bookId: String,
    ) : IntakeResult

    data class Duplicate(
        val bookId: String,
    ) : IntakeResult

    data class Rejected(
        val reason: ImportFailure,
    ) : IntakeResult
}

/**
 * First half of an import, run while the app still holds read access to the
 * shared or picked file: checks the content signature, copies the file into
 * private storage with a size limit while hashing it, rejects duplicates,
 * creates the book row and queues [ImportBookWorker] (SR-7: only `content://`
 * URIs are accepted).
 *
 * Never throws for a bad file, a misbehaving provider or a storage error:
 * the sharing app's provider is code of another app and may throw anything
 * when its file or its name is read, and all of that is a rejection. Books are
 * registered one at a time, so the same file shared twice at once is added
 * once; if another intake still adds it first, the second is reported as a
 * duplicate.
 */
@Singleton
class BookIntake
    @Inject
    constructor(
        private val contentResolver: ContentResolver,
        private val bookDao: BookDao,
        private val files: BookFileStore,
        private val jobs: ImportJobs,
        private val clock: Clock,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** Registers one book at a time: the duplicate check and the new row go together. */
        private val registering = Mutex()

        suspend fun import(uri: Uri): IntakeResult =
            withContext(io) {
                if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                    IntakeResult.Rejected(ImportFailure.UNSUPPORTED_FORMAT)
                } else {
                    guarded("open shared file") {
                        val stream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No stream")
                        stream.use { importStream(it, displayTitle(uri)) }
                    }
                }
            }

        /**
         * Imports [file], a book the app downloaded itself (the catalogue), with the same checks as a shared file:
         * signature, size limit and duplicates. The file is copied, so the caller still owns it.
         */
        suspend fun importFile(
            file: File,
            fallbackTitle: String,
        ): IntakeResult =
            withContext(io) {
                guarded("open downloaded book") { file.inputStream().use { importStream(it, fallbackTitle) } }
            }

        /** Imports from an already opened stream; separated from [import] for testing. */
        internal suspend fun importStream(
            input: InputStream,
            fallbackTitle: String,
        ): IntakeResult =
            withContext(io) {
                val temp = files.newImportTempFile()
                try {
                    guarded("copy import") {
                        val copy = copyWithLimit(input, temp)
                        when (val format = detectFormat(temp)) {
                            null -> IntakeResult.Rejected(ImportFailure.UNSUPPORTED_FORMAT)
                            else -> registering.withLock { register(temp, format, copy, fallbackTitle) }
                        }
                    }
                } finally {
                    temp.delete()
                }
            }

        /**
         * Runs [block], turning what a provider or the storage throws into a rejection: a file over the size limit is
         * too large, anything else could not be stored. Cancellation goes through.
         */
        @Suppress("TooGenericExceptionCaught") // Providers are other apps' code and throw whatever they like.
        private inline fun guarded(
            where: String,
            block: () -> IntakeResult,
        ): IntakeResult =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.recordHandled(where, e)
                IntakeResult.Rejected(
                    if (e is TooLargeException) ImportFailure.TOO_LARGE else ImportFailure.STORAGE_ERROR,
                )
            }

        /**
         * Adds the copied [temp] file as a new book, unless the same file is already in the library. Runs under
         * [registering]; when another intake (not serialized with this one) added the same file first, the unique index
         * on its hash refuses the row, and this copy is deleted.
         */
        private suspend fun register(
            temp: File,
            format: BookFormat,
            copy: CopiedFile,
            title: String,
        ): IntakeResult {
            val existing = bookDao.findBySourceSha256(copy.sha256)
            if (existing != null) return retryOrReportDuplicate(existing)
            val bookId = UUID.randomUUID().toString()
            files.adoptSource(temp, bookId, format)
            return try {
                bookDao.insert(newBook(bookId, title, format, copy))
                jobs.enqueue(bookId)
                IntakeResult.Queued(bookId)
            } catch (e: SQLiteConstraintException) {
                Diagnostics.recordHandled("register import", e)
                files.delete(bookId)
                IntakeResult.Duplicate(bookDao.findBySourceSha256(copy.sha256)?.id ?: throw e)
            }
        }

        /**
         * The same file again: a book that failed before (for example a format
         * supported only in a later version) is prepared again instead of being
         * reported as already in the library.
         */
        private suspend fun retryOrReportDuplicate(existing: BookEntity): IntakeResult =
            if (existing.status == BookStatus.FAILED) {
                bookDao.markImporting(existing.id)
                jobs.enqueue(existing.id)
                IntakeResult.Queued(existing.id)
            } else {
                IntakeResult.Duplicate(existing.id)
            }

        private fun detectFormat(file: File): BookFormat? {
            val header = file.inputStream().use { it.readUpTo(BookFormatDetector.HEADER_SIZE) }
            return when (val detection = BookFormatDetector.detect(header)) {
                is FormatDetection.Detected -> detection.format

                // Non-conforming EPUBs are ZIPs without the marker; the EPUB parser decides.
                FormatDetection.ZipArchive -> BookFormat.EPUB

                FormatDetection.Unsupported -> null
            }
        }

        private fun copyWithLimit(
            input: InputStream,
            target: File,
        ): CopiedFile {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) throw TooLargeException()
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            return CopiedFile(digest.digest().joinToString("") { "%02x".format(it) }, total)
        }

        /**
         * The row of a book just received. Its [title] comes from the file name, which the sharing app or the catalogue
         * chose, so it is cut like any title (SR-13).
         */
        private fun newBook(
            bookId: String,
            title: String,
            format: BookFormat,
            copy: CopiedFile,
        ) = BookEntity(
            id = bookId,
            title = MetadataLimits.title(title),
            authors = emptyList(),
            language = null,
            description = null,
            format = format.name,
            sourceSha256 = copy.sha256,
            sourceSize = copy.size,
            status = BookStatus.IMPORTING,
            failureReason = null,
            totalChars = 0,
            chapterCount = 0,
            hasCover = false,
            addedAt = clock.millis(),
            lastOpenedAt = null,
            archived = false,
            voiceId = null,
        )

        /** Title derived from the file name, used until the book's own metadata is read. */
        private fun displayTitle(uri: Uri): String {
            val name =
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            return name
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.trim()
                .orEmpty()
        }

        /** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
        private fun InputStream.readUpTo(limit: Int): ByteArray {
            val buffer = ByteArray(limit)
            var filled = 0
            while (filled < limit) {
                val read = read(buffer, filled, limit - filled)
                if (read < 0) break
                filled += read
            }
            return buffer.copyOf(filled)
        }

        private data class CopiedFile(
            val sha256: String,
            val size: Long,
        )

        private class TooLargeException : IOException("File exceeds the import size limit")

        companion object {
            /** Largest file accepted for import (SR-2 style limit at the intake boundary). */
            const val MAX_IMPORT_BYTES: Long = 300L * 1024 * 1024
            private const val BUFFER_SIZE = 64 * 1024
        }
    }
