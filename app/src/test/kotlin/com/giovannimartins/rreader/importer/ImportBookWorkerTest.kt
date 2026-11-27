package com.giovannimartins.rreader.importer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.NormalizedBook
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
import java.util.Collections

/** The import job's progress, foreground service and notification, with a parser that reports progress. */
@RunWith(RobolectricTestRunner::class)
class ImportBookWorkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RReaderDatabase
    private val published: MutableList<Data> = Collections.synchronizedList(mutableListOf())
    private val foregrounds: MutableList<ForegroundInfo> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { database.bookDao().insert(importingBook()) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `publishes the recognition progress and runs it in a dataSync foreground service`() {
        val result =
            run(foregroundUpdater = { _, _, info ->
                foregrounds += info
                Futures.immediateVoidFuture()
            })

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(runBlocking { database.bookDao().findById(BOOK_ID)!!.status }).isEqualTo(BookStatus.READY)
        val reported = published.mapNotNull(ImportProgressData::read)
        assertThat(reported).isNotEmpty()
        assertThat(reported.last()).isEqualTo(BOOK_ID to ImportProgress.RecognizingText(2, 2))
        assertThat(foregrounds).isNotEmpty()
        foregrounds.forEach { info ->
            assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            assertThat(info.notificationId).isEqualTo(ImportNotifications.notificationId(BOOK_ID))
        }
        assertThat(
            foregrounds
                .last()
                .notification.extras
                .getString(Notification.EXTRA_TEXT),
        ).isEqualTo("Recognizing text: page 2 of 2")
    }

    @Test
    fun `goes on without the foreground service when the platform refuses it`() {
        var attempts = 0

        val result =
            run(foregroundUpdater = { _, _, _ ->
                attempts++
                Futures.immediateFailedFuture(IllegalStateException("foreground service not allowed"))
            })

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(runBlocking { database.bookDao().findById(BOOK_ID)!!.status }).isEqualTo(BookStatus.READY)
        assertThat(attempts).isEqualTo(1)
        assertThat(published.mapNotNull(ImportProgressData::read).last())
            .isEqualTo(BOOK_ID to ImportProgress.RecognizingText(2, 2))
    }

    @Test
    fun `goes on when its progress cannot be published`() {
        val worker =
            TestListenableWorkerBuilder<ImportBookWorker>(context)
                .setInputData(ImportBookWorker.inputFor(BOOK_ID))
                .setWorkerFactory(factory())
                .setProgressUpdater {
                    _,
                    _,
                    _,
                    ->
                    Futures.immediateFailedFuture(IllegalStateException("database closed"))
                }.setForegroundUpdater { _, _, _ -> Futures.immediateVoidFuture() }
                .build()

        assertThat(runBlocking { worker.doWork() }).isEqualTo(ListenableWorker.Result.success())
        assertThat(runBlocking { database.bookDao().findById(BOOK_ID)!!.status }).isEqualTo(BookStatus.READY)
    }

    @Test
    fun `gives up an import started too many times, marking the book too complex`() {
        var parses = 0
        val worker =
            TestListenableWorkerBuilder<ImportBookWorker>(context)
                .setInputData(ImportBookWorker.inputFor(BOOK_ID))
                .setRunAttemptCount(ImportBookWorker.MAX_RUN_ATTEMPTS)
                .setWorkerFactory(factory(onParse = { parses++ }))
                .build()

        assertThat(runBlocking { worker.doWork() }).isEqualTo(ListenableWorker.Result.failure())
        val book = runBlocking { database.bookDao().findById(BOOK_ID)!! }
        assertThat(book.status).isEqualTo(BookStatus.FAILED)
        assertThat(book.failureReason).isEqualTo(ImportFailure.TOO_COMPLEX)
        assertThat(parses).isEqualTo(0)
    }

    @Test
    fun `still imports on the last run allowed`() {
        val worker =
            TestListenableWorkerBuilder<ImportBookWorker>(context)
                .setInputData(ImportBookWorker.inputFor(BOOK_ID))
                .setRunAttemptCount(ImportBookWorker.MAX_RUN_ATTEMPTS - 1)
                .setWorkerFactory(factory())
                .setProgressUpdater { _, _, _ -> Futures.immediateVoidFuture() }
                .setForegroundUpdater { _, _, _ -> Futures.immediateVoidFuture() }
                .build()

        assertThat(runBlocking { worker.doWork() }).isEqualTo(ListenableWorker.Result.success())
        assertThat(runBlocking { database.bookDao().findById(BOOK_ID)!!.status }).isEqualTo(BookStatus.READY)
    }

    @Test
    fun `fails a job without a book`() {
        val worker =
            TestListenableWorkerBuilder<ImportBookWorker>(context)
                .setWorkerFactory(factory())
                .build()

        assertThat(runBlocking { worker.doWork() }).isEqualTo(ListenableWorker.Result.failure())
    }

    private fun run(foregroundUpdater: ForegroundUpdater): ListenableWorker.Result {
        val worker =
            TestListenableWorkerBuilder<ImportBookWorker>(context)
                .setInputData(ImportBookWorker.inputFor(BOOK_ID))
                .setWorkerFactory(factory())
                .setProgressUpdater(
                    ProgressUpdater { _, _, data ->
                        published += data
                        Futures.immediateVoidFuture()
                    },
                ).setForegroundUpdater(foregroundUpdater)
                .build()
        return runBlocking { worker.doWork() }
    }

    private fun factory(onParse: () -> Unit = {}) =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = ImportBookWorker(appContext, workerParameters, processor(onParse), ImportNotifications(appContext))
        }

    /** Imports with a parser that reports recognizing two pages, then returns a book. */
    private fun processor(onParse: () -> Unit) =
        BookImportProcessor(
            bookDao = database.bookDao(),
            files = BookFileStore(folder.root),
            parsers =
                mapOf(
                    BookFormat.PDF to
                        BookParser { request ->
                            onParse()
                            request.progress.onProgress(ImportProgress.RecognizingText(1, 2))
                            request.progress.onProgress(ImportProgress.RecognizingText(2, 2))
                            ParseOutcome.Parsed(
                                NormalizedBook(BookMetadata("Iracema"), listOf(Chapter(null, "Verdes mares."))),
                            )
                        },
                ),
            io = Dispatchers.IO,
            cpu = Dispatchers.Default,
        )

    private fun importingBook() =
        BookEntity(
            id = BOOK_ID,
            title = "iracema",
            authors = emptyList(),
            language = null,
            description = null,
            format = BookFormat.PDF.name,
            sourceSha256 = "a",
            sourceSize = 1,
            status = BookStatus.IMPORTING,
            failureReason = null,
            totalChars = 0,
            chapterCount = 0,
            hasCover = false,
            addedAt = 0,
            lastOpenedAt = null,
            archived = false,
            voiceId = null,
        )

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
