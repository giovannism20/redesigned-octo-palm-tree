package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.IntakeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Why a catalogue book could not be added to the library, as the listener is told. */
enum class DownloadFailure {
    /** The internet connection was missing or broke, also on the last attempt. */
    NETWORK,

    /** Gutenberg did not deliver the book: busy, refusing, or answering with something else than the book. */
    UNAVAILABLE,

    /** Gutenberg does not have this book any more. */
    NOT_FOUND,

    /** The book is larger than [CatalogDownloader.MAX_DOWNLOAD_BYTES]. */
    TOO_LARGE,

    /** The file is not a book the app can read. */
    NOT_A_BOOK,

    /** The book could not be saved on the phone. */
    STORAGE,
}

/** Outcome of downloading a catalogue book. */
sealed interface DownloadOutcome {
    /** The book is in the library as [bookId]; [alreadyThere] when it was there before this download. */
    data class InLibrary(
        val bookId: String,
        val alreadyThere: Boolean,
    ) : DownloadOutcome

    /** The book was not added; a [retryable] failure may work on a later attempt. */
    data class Failed(
        val reason: DownloadFailure,
        val retryable: Boolean,
    ) : DownloadOutcome
}

/**
 * Downloads a catalogue book and hands it to the import like any file the listener adds. The EPUB is fetched through
 * [CatalogHttp] (allowlisted HTTPS, only `application/epub+zip`, at most [MAX_DOWNLOAD_BYTES]) into a temporary file
 * in private storage; then [BookIntake] checks its signature, copies it, recognizes a book already in the library and
 * queues its preparation, after which the EPUB parser validates it within its limits. The temporary file is always
 * deleted.
 */
class CatalogDownloader
    @Inject
    constructor(
        private val http: CatalogHttp,
        private val intake: BookIntake,
        private val files: BookFileStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** Downloads ebook [id], titled [title] until its own metadata is read, telling [onProgress] how it goes. */
        suspend fun download(
            id: Int,
            title: String,
            onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
        ): DownloadOutcome =
            withContext(io) {
                val temp = files.newImportTempFile()
                try {
                    val request = CatalogRequest(GutenbergEndpoints.epub(id), EPUB_TYPES, MAX_DOWNLOAD_BYTES)
                    when (val fetched = temp.outputStream().use { http.fetch(request, it, onProgress) }) {
                        is FetchResult.Failed -> failed(fetched.failure)
                        is FetchResult.Received -> outcomeOf(intake.importFile(temp, title))
                    }
                } catch (e: IOException) {
                    Diagnostics.recordHandled("save downloaded book", e)
                    DownloadOutcome.Failed(DownloadFailure.STORAGE, retryable = false)
                } finally {
                    temp.delete()
                }
            }

        private fun failed(failure: FetchFailure): DownloadOutcome.Failed {
            val reason =
                when (failure) {
                    FetchFailure.NETWORK -> {
                        DownloadFailure.NETWORK
                    }

                    FetchFailure.NOT_FOUND -> {
                        DownloadFailure.NOT_FOUND
                    }

                    FetchFailure.TOO_LARGE -> {
                        DownloadFailure.TOO_LARGE
                    }

                    FetchFailure.SERVER, FetchFailure.REFUSED, FetchFailure.WRONG_TYPE, FetchFailure.BLOCKED -> {
                        DownloadFailure.UNAVAILABLE
                    }
                }
            return DownloadOutcome.Failed(reason, failure.retryable)
        }

        private fun outcomeOf(result: IntakeResult): DownloadOutcome =
            when (result) {
                is IntakeResult.Queued -> DownloadOutcome.InLibrary(result.bookId, alreadyThere = false)
                is IntakeResult.Duplicate -> DownloadOutcome.InLibrary(result.bookId, alreadyThere = true)
                is IntakeResult.Rejected -> DownloadOutcome.Failed(reasonOf(result.reason), retryable = false)
            }

        private fun reasonOf(failure: ImportFailure): DownloadFailure =
            when (failure) {
                ImportFailure.TOO_LARGE -> DownloadFailure.TOO_LARGE
                ImportFailure.STORAGE_ERROR -> DownloadFailure.STORAGE
                else -> DownloadFailure.NOT_A_BOOK
            }

        companion object {
            /** The largest book downloaded, below the 300 MB import limit: Gutenberg's EPUBs are far smaller. */
            const val MAX_DOWNLOAD_BYTES: Long = 100L * 1024 * 1024

            private val EPUB_TYPES = setOf("application/epub+zip")
        }
    }
