package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.giovannimartins.rreader.Diagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background job that downloads one catalogue book into the library ([CatalogDownloader]); it survives leaving the
 * screen and process death. It runs only with a connection (see [WorkManagerCatalogDownloads]). A failure that may
 * pass (no connection, Gutenberg's site and its mirror down or busy) is tried again after a pause, but no new try
 * starts later than [DownloadRetryPolicy.deadline] after the first one (ADR 0020): then the job fails and its output
 * tells why, so the listener is never left waiting without knowing. Meanwhile it publishes what it is doing:
 * receiving the book from the site or from the mirror, or waiting to try again. The job's output tells the book's id
 * in the library, or why it was not added.
 */
@HiltWorker
class DownloadBookWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val downloader: CatalogDownloader,
        private val policy: DownloadRetryPolicy,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id =
                DownloadWork.idOf(inputData)
                    ?: return Result.failure(DownloadWork.failed(DownloadFailure.UNAVAILABLE))
            // The download reports from an I/O thread; the latest report is published, and the last one always is.
            val reports = Channel<Data>(Channel.CONFLATED)
            val outcome =
                coroutineScope {
                    launch { for (progress in reports) publish(progress) }
                    try {
                        downloadTryingAgain(id, reports)
                    } finally {
                        reports.close()
                    }
                }
            return when (outcome) {
                is DownloadOutcome.InLibrary -> Result.success(DownloadWork.inLibrary(outcome))
                is DownloadOutcome.Failed -> Result.failure(DownloadWork.failed(outcome.reason))
            }
        }

        /** Downloads ebook [id], trying again after failures that may pass while the policy's deadline allows. */
        private suspend fun downloadTryingAgain(
            id: Int,
            reports: SendChannel<Data>,
        ): DownloadOutcome {
            val started = policy.timeSource.markNow()
            val title = DownloadWork.titleOf(inputData)
            var outcome = downloader.download(id, title, reporter(reports))
            while (outcome is DownloadOutcome.Failed &&
                outcome.retryable &&
                started.elapsedNow() + policy.pause < policy.deadline
            ) {
                reports.trySend(DownloadWork.retrying())
                delay(policy.pause)
                outcome = downloader.download(id, title, reporter(reports))
            }
            return outcome
        }

        /** Sends where the book comes from and the percentage received, or null while the size is unknown. */
        private fun reporter(reports: SendChannel<Data>): (DownloadProgress) -> Unit {
            var last: Pair<Boolean, Int?>? = null
            return { progress ->
                val percent = if (progress.total > 0) (progress.received * PERCENT / progress.total).toInt() else null
                val report = progress.fromMirror to percent
                if (report != last) {
                    last = report
                    reports.trySend(DownloadWork.progress(percent, progress.fromMirror))
                }
            }
        }

        @Suppress("TooGenericExceptionCaught") // WorkManager fails progress updates in several ways; none must stop it.
        private suspend fun publish(progress: Data) {
            try {
                setProgress(progress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.recordHandled("publish download progress", e)
            }
        }

        private companion object {
            const val PERCENT = 100
        }
    }
