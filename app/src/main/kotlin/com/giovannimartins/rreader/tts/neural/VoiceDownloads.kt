package com.giovannimartins.rreader.tts.neural

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import kotlin.math.roundToInt

/** Where the download of a voice package stands; an installed package has no download state. */
sealed interface VoiceDownloadState {
    /** Queued: waiting for a connection (a Wi-Fi or other unmetered one when [wifiOnly]), or for the next attempt. */
    data class Waiting(
        val wifiOnly: Boolean,
    ) : VoiceDownloadState

    /** Receiving the archive; [fraction] is between 0 and 1. */
    data class Downloading(
        val fraction: Float,
    ) : VoiceDownloadState

    /** Checking and unpacking the archive. */
    data object Installing : VoiceDownloadState

    data class Failed(
        val reason: VoiceDownloadFailure,
    ) : VoiceDownloadState
}

/** The downloads of voice packages, which run in the background and survive leaving the screen. */
interface VoiceDownloads {
    /**
     * Downloads and installs [pkg]. With [wifiOnly] it waits for Wi-Fi (or another unmetered network) and pauses when
     * the phone leaves it; otherwise any connection will do, mobile data included. A download already running is
     * left alone; a waiting or failed one is replaced by this request.
     */
    suspend fun download(
        pkg: VoicePackage,
        wifiOnly: Boolean,
    )

    /** Stops the download of [pkg] and deletes what it received. */
    suspend fun cancel(pkg: VoicePackage)

    /** The downloads WorkManager knows about, by package id. */
    val states: Flow<Map<String, VoiceDownloadState>>
}

/**
 * [VoiceDownloads] on WorkManager: one unique [VoiceDownloadWorker] job per package that needs a connection of the
 * chosen kind and enough storage, retried with an exponential backoff from [RETRY_DELAY] (each attempt resumes the
 * download where the previous one stopped).
 */
class WorkManagerVoiceDownloads
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val downloader: VoicePackageDownloader,
    ) : VoiceDownloads {
        override suspend fun download(
            pkg: VoicePackage,
            wifiOnly: Boolean,
        ) {
            val name = VoiceDownloadWork.uniqueName(pkg)
            val running =
                workManager.getWorkInfosForUniqueWorkFlow(name).first().any {
                    it.state ==
                        WorkInfo.State.RUNNING
                }
            val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request =
                OneTimeWorkRequestBuilder<VoiceDownloadWorker>()
                    .setInputData(VoiceDownloadWork.input(pkg))
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(network)
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY)
                    .addTag(VoiceDownloadWork.TAG)
                    .addTag(VoiceDownloadWork.packageTag(pkg))
                    .apply { if (wifiOnly) addTag(VoiceDownloadWork.WIFI_ONLY_TAG) }
                    .build()
            val policy = if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            workManager.enqueueUniqueWork(name, policy, request)
        }

        override suspend fun cancel(pkg: VoicePackage) {
            workManager.cancelUniqueWork(VoiceDownloadWork.uniqueName(pkg))
            downloader.discard(pkg)
        }

        override val states: Flow<Map<String, VoiceDownloadState>> =
            workManager
                .getWorkInfosByTagFlow(VoiceDownloadWork.TAG)
                .map { jobs -> jobs.mapNotNull(VoiceDownloadWork::stateOf).toMap() }
                .distinctUntilChanged()

        companion object {
            /** The wait before the first retry; it doubles after each failed attempt. */
            val RETRY_DELAY: Duration = Duration.ofSeconds(30)
        }
    }

/** How voice download jobs are named and tagged, and what they keep in WorkManager's [Data]. */
internal object VoiceDownloadWork {
    /** Tag of every voice download job. */
    const val TAG = "voice-download"
    const val WIFI_ONLY_TAG = "voice-download-wifi-only"
    private const val PACKAGE_TAG_PREFIX = "voice-package:"
    private const val PACKAGE = "package"
    private const val PHASE = "phase"
    private const val PERCENT = "percent"
    private const val FAILURE = "failure"
    private const val INSTALLING = "installing"
    private const val DOWNLOADING = "downloading"
    private const val MAX_PERCENT = 100
    private const val FULL = 100f

    fun uniqueName(pkg: VoicePackage) = "voice-download-${pkg.id}"

    fun packageTag(pkg: VoicePackage) = PACKAGE_TAG_PREFIX + pkg.id

    fun input(pkg: VoicePackage): Data = workDataOf(PACKAGE to pkg.id)

    /** The package in [input], or null when it names none of the catalogue. */
    fun packageOf(input: Data): VoicePackage? = NeuralVoices.packageById(input.getString(PACKAGE))

    fun progress(progress: VoiceDownloadProgress): Data =
        when (progress) {
            is VoiceDownloadProgress.Downloading -> {
                workDataOf(
                    PHASE to DOWNLOADING,
                    PERCENT to (progress.fraction * FULL).roundToInt().coerceIn(0, MAX_PERCENT),
                )
            }

            VoiceDownloadProgress.Installing -> {
                workDataOf(PHASE to INSTALLING)
            }
        }

    fun failed(reason: VoiceDownloadFailure): Data = workDataOf(FAILURE to reason.name)

    /** The package id and the state of the download job [job], or null for a job that tells nothing. */
    fun stateOf(job: WorkInfo): Pair<String, VoiceDownloadState>? {
        val id =
            job.tags.firstNotNullOfOrNull { tag ->
                tag.takeIf { it.startsWith(PACKAGE_TAG_PREFIX) }?.removePrefix(PACKAGE_TAG_PREFIX)
            }
        val state =
            when (job.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> VoiceDownloadState.Waiting(WIFI_ONLY_TAG in job.tags)
                WorkInfo.State.RUNNING -> running(job.progress)
                WorkInfo.State.FAILED -> VoiceDownloadState.Failed(failureOf(job.outputData))
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
            }
        return if (id == null || state == null) null else id to state
    }

    private fun running(progress: Data): VoiceDownloadState =
        if (progress.getString(PHASE) == INSTALLING) {
            VoiceDownloadState.Installing
        } else {
            VoiceDownloadState.Downloading(progress.getInt(PERCENT, 0).coerceIn(0, MAX_PERCENT) / FULL)
        }

    private fun failureOf(output: Data): VoiceDownloadFailure =
        VoiceDownloadFailure.entries.firstOrNull { it.name == output.getString(FAILURE) }
            ?: VoiceDownloadFailure.UNAVAILABLE
}
