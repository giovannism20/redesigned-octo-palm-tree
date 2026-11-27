package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.giovannimartins.rreader.Diagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Background job that downloads one catalogue book into the library ([CatalogDownloader]); it survives leaving the
 * screen and process death. It runs only with a connection (see [WorkManagerCatalogDownloads]). A failure that may
 * pass (no connection, a busy server) is retried, at most [MAX_ATTEMPTS] attempts in all; the job's output tells the
 * book's id in the library, or why it was not added.
 */
@HiltWorker
class DownloadBookWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val downloader: CatalogDownloader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id =
                DownloadWork.idOf(inputData)
                    ?: return Result.failure(DownloadWork.failed(DownloadFailure.UNAVAILABLE))
            // The download reports from an I/O thread; the latest percentage is published, and the last one always is.
            val reports = Channel<Int?>(Channel.CONFLATED)
            val outcome =
                coroutineScope {
                    launch { for (percent in reports) publish(percent) }
                    try {
                        downloader.download(id, DownloadWork.titleOf(inputData), reporter(reports))
                    } finally {
                        reports.close()
                    }
                }
            return when (outcome) {
                is DownloadOutcome.InLibrary -> Result.success(DownloadWork.inLibrary(outcome))
                is DownloadOutcome.Failed -> retryOrFail(outcome)
            }
        }

        private fun retryOrFail(outcome: DownloadOutcome.Failed): Result =
            if (outcome.retryable && runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(DownloadWork.failed(outcome.reason))
            }

        /** Sends the percentage received, or null while the size is unknown, each time it changes. */
        private fun reporter(reports: SendChannel<Int?>): (Long, Long) -> Unit {
            var last: Int? = NOT_REPORTED
            return { received, total ->
                val percent = if (total > 0) (received * PERCENT / total).toInt() else null
                if (percent != last) {
                    last = percent
                    reports.trySend(percent)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught") // WorkManager fails progress updates in several ways; none must stop it.
        private suspend fun publish(percent: Int?) {
            try {
                setProgress(DownloadWork.progress(percent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.recordHandled("publish download progress", e)
            }
        }

        companion object {
            /** Attempts at most, the first included, for failures that may pass. */
            const val MAX_ATTEMPTS: Int = 4

            private const val PERCENT = 100
            private const val NOT_REPORTED = -1
        }
    }
