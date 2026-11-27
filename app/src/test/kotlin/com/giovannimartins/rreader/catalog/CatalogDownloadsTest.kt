package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CatalogDownloadsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        // WorkManager's own bookkeeping runs synchronously too, so no task outlives its test.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `queues one download per book, which waits for a connection`() {
        val downloads = WorkManagerCatalogDownloads(workManager)

        downloads.download(DOM_CASMURRO)
        downloads.download(DOM_CASMURRO)

        val job = workManager.getWorkInfosForUniqueWork(DownloadWork.uniqueName(DOM_CASMURRO.id)).get().single()
        assertThat(job.tags).containsAtLeast(DownloadWork.TAG, DownloadWork.bookTag(DOM_CASMURRO.id))
        assertThat(job.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(job.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(runBlocking { downloads.states.first() }).containsExactly(DOM_CASMURRO.id, DownloadState.Waiting)
    }

    @Test
    fun `reads where each download stands from its job`() {
        val done = DownloadWork.inLibrary(DownloadOutcome.InLibrary("livro-1", alreadyThere = true))

        assertThat(state(WorkInfo.State.ENQUEUED)).isEqualTo(DownloadState.Waiting)
        assertThat(state(WorkInfo.State.BLOCKED)).isEqualTo(DownloadState.Waiting)
        assertThat(state(WorkInfo.State.RUNNING)).isEqualTo(DownloadState.Downloading(null))
        assertThat(state(WorkInfo.State.RUNNING, progress = DownloadWork.progress(45)))
            .isEqualTo(DownloadState.Downloading(0.45f))
        assertThat(state(WorkInfo.State.RUNNING, progress = DownloadWork.progress(null)))
            .isEqualTo(DownloadState.Downloading(null))
        assertThat(state(WorkInfo.State.SUCCEEDED, output = done)).isEqualTo(DownloadState.InLibrary("livro-1", true))
        assertThat(state(WorkInfo.State.FAILED, output = DownloadWork.failed(DownloadFailure.TOO_LARGE)))
            .isEqualTo(DownloadState.Failed(DownloadFailure.TOO_LARGE))
        assertThat(state(WorkInfo.State.FAILED)).isEqualTo(DownloadState.Failed(DownloadFailure.UNAVAILABLE))
        assertThat(state(WorkInfo.State.CANCELLED)).isNull()
    }

    @Test
    fun `ignores jobs without a book`() {
        val job = WorkInfo(UUID.randomUUID(), WorkInfo.State.ENQUEUED, setOf(DownloadWork.TAG))

        assertThat(DownloadWork.stateOf(job)).isNull()
    }

    private fun state(
        state: WorkInfo.State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): DownloadState? {
        val tags = setOf(DownloadWork.TAG, DownloadWork.bookTag(DOM_CASMURRO.id))
        val job = WorkInfo(UUID.randomUUID(), state, tags, outputData = output, progress = progress)
        return DownloadWork.stateOf(job)?.also { assertThat(it.first).isEqualTo(DOM_CASMURRO.id) }?.second
    }
}
