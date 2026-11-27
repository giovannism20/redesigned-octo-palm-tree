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
 * A failure that may pass is retried, at most [MAX_ATTEMPTS] attempts in all, each resuming the download; the job's
 * output tells why a package was not installed.
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
            val outcome = reportingProgress(VoiceDownloadWork::progress) { report -> downloader.download(pkg, report) }
            return when (outcome) {
                VoiceDownloadOutcome.Installed -> {
                    Result.success()
                }

                is VoiceDownloadOutcome.Failed -> {
                    if (outcome.retryable && runAttemptCount + 1 < MAX_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure(VoiceDownloadWork.failed(outcome.reason))
                    }
                }
            }
        }

        companion object {
            /** Attempts at most, the first included; each one continues the download where the last stopped. */
            const val MAX_ATTEMPTS: Int = 6
        }
    }
