package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.EPUB
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ok
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.redirect
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.FakeImportJobs
import com.giovannimartins.rreader.importer.MinimalEpub
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

/** The download job: what it reports, when it retries and what it leaves for the Explore screen. */
@RunWith(RobolectricTestRunner::class)
class DownloadBookWorkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val transport = FakeTransport()
    private val published: MutableList<Data> = Collections.synchronizedList(mutableListOf())
    private lateinit var database: RReaderDatabase
    private lateinit var downloader: CatalogDownloader

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        val files = BookFileStore(folder.root)
        val intake =
            BookIntake(
                contentResolver = context.contentResolver,
                bookDao = database.bookDao(),
                files = files,
                jobs = FakeImportJobs(),
                clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                io = Dispatchers.Unconfined,
            )
        downloader =
            CatalogDownloader(CatalogHttp(transport, Dispatchers.Unconfined), intake, files, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `adds the book, reports its progress and tells its id in the library`() {
        transport.answer(BOOK) { ok(MinimalEpub.bytes(), EPUB) }

        val result = run()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val state = stateAfter(result, WorkInfo.State.SUCCEEDED)
        val bookId = (state as DownloadState.InLibrary).bookId
        assertThat(runBlocking { database.bookDao().findById(bookId) }).isNotNull()
        assertThat(state.alreadyThere).isFalse()
        assertThat(published.map { fractionOf(it) }.last()).isEqualTo(1f)
    }

    @Test
    fun `retries a failure that may pass, until the last attempt`() {
        assertThat(run(attempt = 0)).isEqualTo(ListenableWorker.Result.retry())
        assertThat(run(attempt = DownloadBookWorker.MAX_ATTEMPTS - 2)).isEqualTo(ListenableWorker.Result.retry())

        val last = run(attempt = DownloadBookWorker.MAX_ATTEMPTS - 1)

        assertThat(stateAfter(last, WorkInfo.State.FAILED)).isEqualTo(DownloadState.Failed(DownloadFailure.NETWORK))
    }

    @Test
    fun `does not retry a failure that will not pass`() {
        val body = FakeTransport.Companion.CountingBody(1)
        transport.answer(BOOK) { ok(body, EPUB, contentLength = CatalogDownloader.MAX_DOWNLOAD_BYTES + 1) }

        val result = run(attempt = 0)

        assertThat(stateAfter(result, WorkInfo.State.FAILED)).isEqualTo(DownloadState.Failed(DownloadFailure.TOO_LARGE))
    }

    @Test
    fun `an answer that is not the book fails without retrying`() {
        transport.answer(BOOK) { ok("<html>Down for maintenance</html>".toByteArray(), "text/html") }

        val result = run(attempt = 0)

        assertThat(stateAfter(result, WorkInfo.State.FAILED))
            .isEqualTo(DownloadState.Failed(DownloadFailure.UNAVAILABLE))
    }

    @Test
    fun `a redirect off the allowlist is refused without contacting it, and not retried`() {
        transport.answer(BOOK) { redirect("https://mirror.example/pg55752-images-3.epub") }

        val result = run(attempt = 0)

        assertThat(stateAfter(result, WorkInfo.State.FAILED))
            .isEqualTo(DownloadState.Failed(DownloadFailure.UNAVAILABLE))
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK)
    }

    @Test
    fun `fails a job without a book number`() {
        val result = run(input = Data.EMPTY)

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(transport.requests).isEmpty()
    }

    private fun run(
        attempt: Int = 0,
        input: Data = DownloadWork.input(DOM_CASMURRO),
    ): ListenableWorker.Result {
        val worker =
            TestListenableWorkerBuilder<DownloadBookWorker>(context)
                .setInputData(input)
                .setRunAttemptCount(attempt)
                .setWorkerFactory(factory())
                .setProgressUpdater { _, _, data ->
                    published += data
                    Futures.immediateVoidFuture()
                }.build()
        return runBlocking { worker.doWork() }
    }

    /** The state the Explore screen reads from a job that ended with [result]. */
    private fun stateAfter(
        result: ListenableWorker.Result,
        state: WorkInfo.State,
    ): DownloadState? {
        val job =
            WorkInfo(
                id = UUID.randomUUID(),
                state = state,
                tags = setOf(DownloadWork.TAG, DownloadWork.bookTag(DOM_CASMURRO.id)),
                outputData = result.outputData,
            )
        return DownloadWork.stateOf(job)?.second
    }

    private fun fractionOf(progress: Data): Float? {
        val job =
            WorkInfo(
                id = UUID.randomUUID(),
                state = WorkInfo.State.RUNNING,
                tags = setOf(DownloadWork.bookTag(DOM_CASMURRO.id)),
                progress = progress,
            )
        return (DownloadWork.stateOf(job)?.second as? DownloadState.Downloading)?.fraction
    }

    private fun factory() =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = DownloadBookWorker(appContext, workerParameters, downloader)
        }

    private companion object {
        const val BOOK = "https://www.gutenberg.org/ebooks/55752.epub3.images"
    }
}
