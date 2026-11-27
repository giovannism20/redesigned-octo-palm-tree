package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.IntakeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Why a catalogue book could not be added to the library, as the listener is told. */
enum class DownloadFailure {
    /** The internet connection was missing or broke, also on the last attempt. */
    NETWORK,

    /** Gutenberg did not deliver the book: refusing it, or answering with something else than the book. */
    UNAVAILABLE,

    /**
     * Gutenberg's site is down for maintenance or too busy (HTTP 5xx or 429), also on the last attempt, and its mirror
     * did not deliver the book either.
     */
    SITE_DOWN,

    /** Gutenberg does not have this book any more. */
    NOT_FOUND,

    /** The book is larger than [CatalogDownloader.MAX_DOWNLOAD_BYTES]. */
    TOO_LARGE,

    /** The file is not a book the app can read. */
    NOT_A_BOOK,

    /** The book could not be saved on the phone. */
    STORAGE,
}

/** How a download goes: where the book comes from, and how much of it arrived. */
data class DownloadProgress(
    /** Whether it comes from Gutenberg's official mirror, because the site could not serve it. */
    val fromMirror: Boolean,
    /** The bytes received so far. */
    val received: Long = 0,
    /** The length announced, or -1 while it is unknown. */
    val total: Long = -1,
)

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
 * [CatalogHttp] (allowlisted HTTPS, an EPUB or generic binary media type, at most [MAX_DOWNLOAD_BYTES]) into a
 * temporary file in private storage, from Gutenberg's site or, when the site is down or out of reach, from its
 * official mirror (ADR 0019); then [BookIntake] checks its signature, copies it, recognizes a book already in the
 * library and queues its preparation, after which the EPUB parser validates it within its limits. The temporary file
 * is always deleted.
 */
class CatalogDownloader
    @Inject
    constructor(
        private val http: CatalogHttp,
        private val intake: BookIntake,
        private val files: BookFileStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /**
         * Downloads ebook [id], titled [title] until its own metadata is read, telling [onProgress] where the book
         * comes from as soon as a source is asked, and how much of it arrived.
         */
        suspend fun download(
            id: Int,
            title: String,
            onProgress: (DownloadProgress) -> Unit = {},
        ): DownloadOutcome =
            withContext(io) {
                val temp = files.newImportTempFile()
                try {
                    when (val fetched = fetch(id, temp, onProgress)) {
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

        /**
         * Fetches ebook [id] into [file] from Gutenberg's site; when the site answers that it is down or busy, or
         * cannot be reached, from its mirror instead. When both fail, the site's failure is the one reported.
         */
        private suspend fun fetch(
            id: Int,
            file: File,
            onProgress: (DownloadProgress) -> Unit,
        ): FetchResult {
            val site = fetchInto(file, GutenbergEndpoints.epub(id), fromMirror = false, onProgress)
            val useMirror = site is FetchResult.Failed && site.failure in MIRROR_FAILURES
            return if (useMirror) {
                fetchInto(file, GutenbergEndpoints.mirrorEpub(id), fromMirror = true, onProgress)
                    .takeIf { it is FetchResult.Received } ?: site
            } else {
                site
            }
        }

        /** Fetches [address] into [file], replacing what an earlier attempt wrote. */
        private suspend fun fetchInto(
            file: File,
            address: String,
            fromMirror: Boolean,
            onProgress: (DownloadProgress) -> Unit,
        ): FetchResult {
            onProgress(DownloadProgress(fromMirror))
            return file.outputStream().use { sink ->
                http.fetch(CatalogRequest(address, EPUB_TYPES, MAX_DOWNLOAD_BYTES), sink) { received, total ->
                    onProgress(DownloadProgress(fromMirror, received, total))
                }
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

                    FetchFailure.SERVER -> {
                        DownloadFailure.SITE_DOWN
                    }

                    FetchFailure.REFUSED, FetchFailure.WRONG_TYPE, FetchFailure.BLOCKED -> {
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

            /**
             * The site serves EPUBs as `application/epub+zip`, the mirror as `application/octet-stream`; either way the
             * intake checks that the file is an EPUB.
             */
            private val EPUB_TYPES = setOf("application/epub+zip", "application/octet-stream")

            /** Failures of the site after which the mirror is tried: the site is down, busy or out of reach. */
            private val MIRROR_FAILURES = setOf(FetchFailure.SERVER, FetchFailure.NETWORK)
        }
    }
