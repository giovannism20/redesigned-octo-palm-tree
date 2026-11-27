package com.giovannimartins.rreader.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.importer.ImportJobs
import com.giovannimartins.rreader.importer.ImportProgress
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: RReaderDatabase
    private val jobs = FakeImportJobs()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RReaderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `books being prepared carry the progress their import reports`() =
        runTest {
            val repository = repository()
            database.bookDao().insert(book(IMPORTING_ID, BookStatus.IMPORTING, sha = "a"))
            database.bookDao().insert(book(READY_ID, BookStatus.READY, sha = "b"))
            jobs.progress.value =
                mapOf(
                    IMPORTING_ID to ImportProgress.RecognizingText(2, 5),
                    READY_ID to ImportProgress.RecognizingText(5, 5),
                )

            val books = repository.books.first().associateBy(LibraryBook::id)

            assertThat(books.getValue(IMPORTING_ID).importProgress).isEqualTo(ImportProgress.RecognizingText(2, 5))
            assertThat(books.getValue(READY_ID).importProgress).isNull()
        }

    @Test
    fun `books being prepared without a report have no progress`() =
        runTest {
            val repository = repository()
            database.bookDao().insert(book(IMPORTING_ID, BookStatus.IMPORTING, sha = "a"))

            assertThat(
                repository.books
                    .first()
                    .single()
                    .importProgress,
            ).isNull()
        }

    private fun kotlinx.coroutines.test.TestScope.repository() =
        LibraryRepository(
            bookDao = database.bookDao(),
            files = BookFileStore(folder.root),
            importJobs = jobs,
            io = StandardTestDispatcher(testScheduler),
        )

    private fun book(
        id: String,
        status: BookStatus,
        sha: String,
    ) = BookEntity(
        id = id,
        title = "Livro",
        authors = emptyList(),
        language = null,
        description = null,
        format = "PDF",
        sourceSha256 = sha,
        sourceSize = 1,
        status = status,
        failureReason = null,
        totalChars = 0,
        chapterCount = 0,
        hasCover = false,
        addedAt = 0,
        lastOpenedAt = null,
        archived = false,
        voiceId = null,
    )

    /** Import jobs whose progress the test sets, and which remember what was cancelled. */
    class FakeImportJobs : ImportJobs {
        val enqueued = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override val progress = MutableStateFlow<Map<String, ImportProgress>>(emptyMap())

        override fun enqueue(bookId: String) {
            enqueued += bookId
        }

        override fun cancel(bookId: String) {
            cancelled += bookId
        }
    }

    private companion object {
        const val IMPORTING_ID = "00000000-0000-4000-8000-000000000001"
        const val READY_ID = "00000000-0000-4000-8000-000000000002"
    }
}
