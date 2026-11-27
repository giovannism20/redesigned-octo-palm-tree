package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ImportJobsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var jobs: WorkManagerImportJobs

    @Before
    fun setUp() {
        val longImports =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = LongImportWorker(appContext, workerParameters)
            }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(longImports)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        jobs = WorkManagerImportJobs(workManager)
    }

    @Test
    fun `runs one tagged job per book, and cancelling it stops it`() {
        jobs.enqueue(BOOK_ID)
        jobs.enqueue(BOOK_ID)

        val running = workManager.getWorkInfosForUniqueWork(ImportBookWorker.uniqueName(BOOK_ID)).get().single()
        assertThat(running.tags).contains(WorkManagerImportJobs.TAG)
        assertThat(running.state).isEqualTo(WorkInfo.State.RUNNING)

        jobs.cancel(BOOK_ID)

        assertThat(workManager.getWorkInfoById(running.id).get()!!.state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `reports no progress while no job has published any`() =
        runTest {
            jobs.enqueue(BOOK_ID)

            assertThat(jobs.progress.first()).isEmpty()
        }

    @Test
    fun `maps the progress of running jobs to their books`() {
        val recognizing = ImportProgress.RecognizingText(3, 10)
        val running = job(WorkInfo.State.RUNNING, ImportProgressData.write(BOOK_ID, recognizing))
        val silent = job(WorkInfo.State.RUNNING, Data.EMPTY)
        val finished =
            job(WorkInfo.State.SUCCEEDED, ImportProgressData.write(OTHER_BOOK_ID, ImportProgress.RecognizingText(1, 2)))

        assertThat(WorkManagerImportJobs.runningProgress(listOf(running, silent, finished)))
            .containsExactly(BOOK_ID, recognizing)
    }

    @Test
    fun `reads back what a worker publishes, and nothing from foreign or broken data`() {
        val progress = ImportProgress.RecognizingText(7, 300)

        assertThat(ImportProgressData.read(ImportProgressData.write(BOOK_ID, progress))).isEqualTo(BOOK_ID to progress)
        listOf(
            Data.EMPTY,
            workDataOf("book_id" to BOOK_ID, "stage" to "unknown", "page" to 1, "page_count" to 2),
            workDataOf("book_id" to BOOK_ID, "stage" to "recognizing_text", "page" to 3, "page_count" to 2),
            workDataOf("book_id" to BOOK_ID, "stage" to "recognizing_text", "page" to 0, "page_count" to 2),
        ).forEach { data -> assertThat(ImportProgressData.read(data)).isNull() }
    }

    private fun job(
        state: WorkInfo.State,
        progress: Data,
    ) = WorkInfo(UUID.randomUUID(), state, setOf(WorkManagerImportJobs.TAG), Data.EMPTY, progress)

    /** An import that runs until it is cancelled. */
    class LongImportWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = awaitCancellation()
    }

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_BOOK_ID = "00000000-0000-4000-8000-000000000002"
    }
}
