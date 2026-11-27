package com.giovannimartins.rreader.importer

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The background import jobs: one unique job per book, which survives process death (ADR 0015). */
interface ImportJobs {
    /** Queues the import of [bookId], unless it is already queued or running. */
    fun enqueue(bookId: String)

    /** Cancels the import of [bookId], for example because the book was removed. */
    fun cancel(bookId: String)

    /** What each running import last reported, by book id; imports that reported nothing are absent. */
    val progress: Flow<Map<String, ImportProgress>>
}

/** [ImportJobs] on WorkManager. Jobs are tagged so that the progress of all of them is observed at once. */
class WorkManagerImportJobs
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : ImportJobs {
        override fun enqueue(bookId: String) {
            val request =
                OneTimeWorkRequestBuilder<ImportBookWorker>()
                    .setInputData(ImportBookWorker.inputFor(bookId))
                    .addTag(TAG)
                    .build()
            workManager.enqueueUniqueWork(ImportBookWorker.uniqueName(bookId), ExistingWorkPolicy.KEEP, request)
        }

        override fun cancel(bookId: String) {
            workManager.cancelUniqueWork(ImportBookWorker.uniqueName(bookId))
        }

        override val progress: Flow<Map<String, ImportProgress>> =
            workManager.getWorkInfosByTagFlow(TAG).map(::runningProgress).distinctUntilChanged()

        companion object {
            /** Tag of every import job. */
            const val TAG: String = "import-book"

            /** The progress that the running jobs among [jobs] published, by book id. */
            internal fun runningProgress(jobs: List<WorkInfo>): Map<String, ImportProgress> =
                jobs
                    .filter { it.state == WorkInfo.State.RUNNING }
                    .mapNotNull { ImportProgressData.read(it.progress) }
                    .toMap()
        }
    }

/** How a worker publishes [ImportProgress] in WorkManager's progress [Data], with the id of its book. */
internal object ImportProgressData {
    private const val KEY_BOOK_ID = "book_id"
    private const val KEY_STAGE = "stage"
    private const val KEY_PAGE = "page"
    private const val KEY_PAGE_COUNT = "page_count"
    private const val STAGE_RECOGNIZING_TEXT = "recognizing_text"

    fun write(
        bookId: String,
        progress: ImportProgress,
    ): Data =
        when (progress) {
            is ImportProgress.RecognizingText -> {
                workDataOf(
                    KEY_BOOK_ID to bookId,
                    KEY_STAGE to STAGE_RECOGNIZING_TEXT,
                    KEY_PAGE to progress.page,
                    KEY_PAGE_COUNT to progress.pageCount,
                )
            }
        }

    /** The book id and the progress in [data], or null when it holds none, such as before a job reports. */
    fun read(data: Data): Pair<String, ImportProgress>? {
        val bookId = data.getString(KEY_BOOK_ID) ?: return null
        val page = data.getInt(KEY_PAGE, 0)
        val pageCount = data.getInt(KEY_PAGE_COUNT, 0)
        return if (data.getString(KEY_STAGE) == STAGE_RECOGNIZING_TEXT && page in 1..pageCount) {
            bookId to ImportProgress.RecognizingText(page, pageCount)
        } else {
            null
        }
    }
}
