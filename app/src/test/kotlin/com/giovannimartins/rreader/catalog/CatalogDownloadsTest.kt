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
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    @After
    fun stopWork() {
        // Stop every job (some test workers wait forever) and wait, so nothing from this test
        // runs on after the next test has replaced WorkManager and its database.
        WorkManager
            .getInstance(context)
            .cancelAllWork()
            .result
            .get()
    }

    @Test
    fun `queues one download per book, which waits for a connection`() {
        val downloads = WorkManagerCatalogDownloads(workManager)

        downloads.download(DOM_CASMURRO)
        downloads.download(DOM_CASMURRO)

        val job = workManager.getWorkInfosForUniqueWork(DownloadWork.uniqueName(DOM_CASMURRO.id)).get().single()
        assertThat(job.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(job.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(runBlocking { downloads.states(setOf(DOM_CASMURRO.id, IRACEMA.id)).first() })
            .containsExactly(DOM_CASMURRO.id, DownloadState.Waiting)
    }

    @Test
    fun `SR-8 no tag of a download job names its book, since WorkManager logs tags`() {
        WorkManagerCatalogDownloads(workManager).download(DOM_CASMURRO)

        val job = workManager.getWorkInfosForUniqueWork(DownloadWork.uniqueName(DOM_CASMURRO.id)).get().single()

        assertThat(job.tags.filter { DOM_CASMURRO.id.toString() in it }).isEmpty()
    }

    @Test
    fun `queues the download of a book whose title is far larger than a job's data may be`() {
        val huge = CatalogEntry(DOM_CASMURRO.id, "Dom Casmurro ".repeat(2_000), null)
        val downloads = WorkManagerCatalogDownloads(workManager)

        downloads.download(huge)

        assertThat(runBlocking { downloads.states(setOf(huge.id)).first() })
            .containsExactly(huge.id, DownloadState.Waiting)
        assertThat(DownloadWork.titleOf(DownloadWork.input(huge)).length).isAtMost(MetadataLimits.MAX_TITLE_LENGTH)
    }

    @Test
    fun `knows no download of books never asked for`() {
        val downloads = WorkManagerCatalogDownloads(workManager)

        assertThat(runBlocking { downloads.states(setOf(IRACEMA.id)).first() }).isEmpty()
        assertThat(runBlocking { downloads.states(emptySet()).first() }).isEmpty()
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

    private fun state(
        state: WorkInfo.State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): DownloadState? =
        DownloadWork.stateOf(WorkInfo(UUID.randomUUID(), state, emptySet(), outputData = output, progress = progress))
}
