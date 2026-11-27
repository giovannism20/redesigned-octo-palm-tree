package com.giovannimartins.rreader.importer

import android.content.ContentResolver
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

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
 */
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
        suspend fun import(uri: Uri): IntakeResult =
            withContext(io) {
                if (uri.scheme !=
                    ContentResolver.SCHEME_CONTENT
                ) {
                    return@withContext IntakeResult.Rejected(ImportFailure.UNSUPPORTED_FORMAT)
                }
                val stream =
                    try {
                        contentResolver.openInputStream(uri)
                    } catch (e: IOException) {
                        Diagnostics.recordHandled("open shared file", e)
                        null
                    } catch (e: SecurityException) {
                        Diagnostics.recordHandled("open shared file", e)
                        null
                    } ?: return@withContext IntakeResult.Rejected(ImportFailure.STORAGE_ERROR)
                stream.use { importStream(it, displayTitle(uri)) }
            }

        /** Imports from an already opened stream; separated from [import] for testing. */
        internal suspend fun importStream(
            input: InputStream,
            fallbackTitle: String,
        ): IntakeResult =
            withContext(io) {
                val temp = files.newImportTempFile()
                try {
                    val copy = copyWithLimit(input, temp)
                    val format =
                        detectFormat(temp) ?: return@withContext IntakeResult.Rejected(ImportFailure.UNSUPPORTED_FORMAT)
                    bookDao.findBySourceSha256(copy.sha256)?.let { existing ->
                        return@withContext retryOrReportDuplicate(existing)
                    }
                    val bookId = UUID.randomUUID().toString()
                    files.adoptSource(temp, bookId, format)
                    bookDao.insert(newBook(bookId, fallbackTitle, format, copy))
                    jobs.enqueue(bookId)
                    IntakeResult.Queued(bookId)
                } catch (e: TooLargeException) {
                    Diagnostics.recordHandled("copy import", e)
                    IntakeResult.Rejected(ImportFailure.TOO_LARGE)
                } catch (e: IOException) {
                    Diagnostics.recordHandled("copy import", e)
                    IntakeResult.Rejected(ImportFailure.STORAGE_ERROR)
                } finally {
                    temp.delete()
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

        private fun newBook(
            bookId: String,
            title: String,
            format: BookFormat,
            copy: CopiedFile,
        ) = BookEntity(
            id = bookId,
            title = title,
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
