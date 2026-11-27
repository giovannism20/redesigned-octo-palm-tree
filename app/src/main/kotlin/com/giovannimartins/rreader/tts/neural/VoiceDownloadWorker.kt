package com.giovannimartins.rreader.tts.neural

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.giovannimartins.rreader.work.reportingProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background job that downloads and installs one voice package ([VoicePackageDownloader]); it survives leaving the
 * screen and process death, and waits for the kind of connection the listener chose (see [WorkManagerVoiceDownloads]).
 * A failure that may pass is retried, each attempt resuming the download, until
 * [VoicePackageDownloader.MAX_STALLED_ATTEMPTS] attempts in a row received nothing new. The attempt count WorkManager
 * keeps would not do: it also grows each time the system stops the job (the Wi-Fi was lost, background work's time
 * limit), which a long download meets several times. The job's output tells why a package was not installed.
 */
@HiltWorker
class VoiceDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val downloader: VoicePackageDownloader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val pkg =
                VoiceDownloadWork.packageOf(inputData)
                    ?: return Result.failure(VoiceDownloadWork.failed(VoiceDownloadFailure.UNAVAILABLE))
            val outcome =
                reportingProgress(VoiceDownloadWork::progress) { report ->
                    downloader.download(pkg, firstAttempt = runAttemptCount == 0, onProgress = report)
                }
            return when (outcome) {
                VoiceDownloadOutcome.Installed -> {
                    Result.success()
                }

                is VoiceDownloadOutcome.Failed -> {
                    if (outcome.retryable) Result.retry() else Result.failure(VoiceDownloadWork.failed(outcome.reason))
                }
            }
        }
    }
