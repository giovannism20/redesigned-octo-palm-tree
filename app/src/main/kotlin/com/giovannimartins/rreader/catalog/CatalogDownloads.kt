package com.giovannimartins.rreader.catalog

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
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

/** Where the download of a catalogue book stands. */
sealed interface DownloadState {
    /** Queued: waiting for a connection, or for the next attempt after a failure that may pass. */
    data object Waiting : DownloadState

    /** Receiving the book; [fraction] is between 0 and 1, or null while its size is unknown. */
    data class Downloading(
        val fraction: Float?,
    ) : DownloadState

    /** The book is in the library as [bookId]; [alreadyThere] when it was there before the download. */
    data class InLibrary(
        val bookId: String,
        val alreadyThere: Boolean,
    ) : DownloadState

    /** The book was not added. */
    data class Failed(
        val reason: DownloadFailure,
    ) : DownloadState
}

/** The downloads of catalogue books, which run in the background and survive leaving the screen. */
interface CatalogDownloads {
    /** Downloads [entry] into the library, unless it is already queued or downloading. */
    fun download(entry: CatalogEntry)

    /** The downloads WorkManager knows about, by Gutenberg ebook number. */
    val states: Flow<Map<Int, DownloadState>>
}

/**
 * [CatalogDownloads] on WorkManager: one unique [DownloadBookWorker] job per ebook that waits for a connection
 * (`NetworkType.CONNECTED`), so a book asked for offline downloads as soon as the phone is back online, and failures
 * that may pass are retried with an exponential backoff from [RETRY_DELAY].
 */
class WorkManagerCatalogDownloads
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : CatalogDownloads {
        override fun download(entry: CatalogEntry) {
            val request =
                OneTimeWorkRequestBuilder<DownloadBookWorker>()
                    .setInputData(DownloadWork.input(entry))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY)
                    .addTag(DownloadWork.TAG)
                    .addTag(DownloadWork.bookTag(entry.id))
                    .build()
            workManager.enqueueUniqueWork(DownloadWork.uniqueName(entry.id), ExistingWorkPolicy.KEEP, request)
        }

        override val states: Flow<Map<Int, DownloadState>> =
            workManager
                .getWorkInfosByTagFlow(DownloadWork.TAG)
                .map { jobs -> jobs.mapNotNull(DownloadWork::stateOf).toMap() }
                .distinctUntilChanged()

        companion object {
            /** The wait before the first retry; it doubles after each failed attempt. */
            val RETRY_DELAY: Duration = Duration.ofSeconds(30)
        }
    }

/** How download jobs are named and tagged, and what they keep in WorkManager's [Data]. */
internal object DownloadWork {
    /** Tag of every download job. */
    const val TAG = "catalog-download"

    fun uniqueName(id: Int) = "catalog-download-$id"

    fun bookTag(id: Int) = "${Keys.BOOK_TAG_PREFIX}$id"

    fun input(entry: CatalogEntry): Data = workDataOf(Keys.ID to entry.id, Keys.TITLE to entry.title)

    /** The ebook number in [input], or null when it holds none. */
    fun idOf(input: Data): Int? = input.getInt(Keys.ID, 0).takeIf { it > 0 }

    fun titleOf(input: Data): String = input.getString(Keys.TITLE).orEmpty()

    /** Progress of [percent] (0–100), or of an unknown share while the size of the book is not known. */
    fun progress(percent: Int?): Data = workDataOf(Keys.PERCENT to (percent ?: Keys.UNKNOWN))

    fun inLibrary(outcome: DownloadOutcome.InLibrary): Data =
        workDataOf(Keys.BOOK_ID to outcome.bookId, Keys.ALREADY_THERE to outcome.alreadyThere)

    fun failed(reason: DownloadFailure): Data = workDataOf(Keys.FAILURE to reason.name)

    /** The ebook number and the state of the download job [job], or null for a job that tells nothing. */
    fun stateOf(job: WorkInfo): Pair<Int, DownloadState>? {
        val id = JobData.bookId(job.tags)
        val state =
            when (job.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Waiting
                WorkInfo.State.RUNNING -> DownloadState.Downloading(JobData.fraction(job.progress))
                WorkInfo.State.SUCCEEDED -> JobData.inLibrary(job.outputData)
                WorkInfo.State.FAILED -> DownloadState.Failed(JobData.failure(job.outputData))
                WorkInfo.State.CANCELLED -> null
            }
        return if (id == null || state == null) null else id to state
    }
}

/** The tag prefix and the [Data] keys of download jobs. */
private object Keys {
    const val BOOK_TAG_PREFIX = "catalog-book:"
    const val ID = "id"
    const val TITLE = "title"
    const val PERCENT = "percent"
    const val BOOK_ID = "book_id"
    const val ALREADY_THERE = "already_there"
    const val FAILURE = "failure"
    const val UNKNOWN = -1
}

/** Reads back what download jobs keep. */
private object JobData {
    private const val PERCENT = 100f

    fun bookId(tags: Set<String>): Int? =
        tags.firstNotNullOfOrNull { tag ->
            tag.takeIf { it.startsWith(Keys.BOOK_TAG_PREFIX) }?.removePrefix(Keys.BOOK_TAG_PREFIX)?.toIntOrNull()
        }

    fun fraction(progress: Data): Float? =
        progress.getInt(Keys.PERCENT, Keys.UNKNOWN).takeIf { it in 0..PERCENT.toInt() }?.let { it / PERCENT }

    fun inLibrary(output: Data): DownloadState? =
        output
            .getString(
                Keys.BOOK_ID,
            )?.let { DownloadState.InLibrary(it, output.getBoolean(Keys.ALREADY_THERE, false)) }

    fun failure(output: Data): DownloadFailure =
        DownloadFailure.entries.firstOrNull { it.name == output.getString(Keys.FAILURE) } ?: DownloadFailure.UNAVAILABLE
}
