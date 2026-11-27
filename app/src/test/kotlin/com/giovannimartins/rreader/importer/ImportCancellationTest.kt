package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.pdf.PageTextRecognizer
import com.giovannimartins.rreader.pdf.PdfFixture
import com.giovannimartins.rreader.pdf.PdfLayoutExtractor
import com.giovannimartins.rreader.pdf.pdfOcr
import com.giovannimartins.rreader.pdf.scannedPage
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.layout.LayoutBookBuilder
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Stopping an import: the book removed while text recognition runs, or the system stopping the job. */
@RunWith(RobolectricTestRunner::class)
class ImportCancellationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RReaderDatabase
    private val files by lazy { BookFileStore(folder.root) }
    private val bookFolder by lazy { files.sourceFile(BOOK_ID, BookFormat.PDF).parentFile!! }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).build()
        val scan = PdfFixture(context).apply { repeat(3) { picturePage() } }
        scan.writeTo(files.sourceFile(BOOK_ID, BookFormat.PDF).apply { parentFile!!.mkdirs() })
        runBlocking { database.bookDao().insert(importingBook()) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `removing a book stops its import and deletes the pages it writes afterwards`() =
        runBlocking {
            val recognizer = SteppedRecognizer()
            val import = launch(Dispatchers.Default) { processor(recognizer).process(BOOK_ID) }
            recognizer.reading(page = 1)
            val jobs = FakeImportJobs()

            LibraryRepository(database.bookDao(), files, jobs, Dispatchers.IO).remove(BOOK_ID)
            // The page being recognized when the book was removed is still written, recreating its folder.
            recognizer.finishPage()
            recognizer.reading(page = 2)
            import.cancelAndJoin()

            assertThat(jobs.cancelled).containsExactly(BOOK_ID)
            assertThat(database.bookDao().findById(BOOK_ID)).isNull()
            assertThat(bookFolder.exists()).isFalse()
        }

    @Test
    fun `a job the system stops keeps the recognized pages, and the next run resumes after them`() =
        runBlocking {
            val stopped = SteppedRecognizer()
            val import = launch(Dispatchers.Default) { processor(stopped).process(BOOK_ID) }
            stopped.reading(page = 1)
            import.cancelAndJoin()

            assertThat(database.bookDao().findById(BOOK_ID)!!.status).isEqualTo(BookStatus.IMPORTING)
            assertThat(files.ocrPageFile(BOOK_ID, 0).isFile).isTrue()
            assertThat(files.ocrPageFile(BOOK_ID, 1).exists()).isFalse()

            val resumed = SteppedRecognizer(blocking = false)
            processor(resumed).process(BOOK_ID)

            assertThat(resumed.requested).containsExactly(1, 2).inOrder()
            assertThat(database.bookDao().findById(BOOK_ID)!!.status).isEqualTo(BookStatus.READY)
        }

    private fun processor(recognizer: PageTextRecognizer) =
        BookImportProcessor(
            bookDao = database.bookDao(),
            files = files,
            parsers =
                mapOf(
                    BookFormat.PDF to
                        PdfBookParser(
                            PdfLayoutExtractor(context),
                            pdfOcr(recognizer, folder.root),
                            { null },
                            LayoutBookBuilder(),
                        ),
                ),
            io = Dispatchers.IO,
            cpu = Dispatchers.Default,
        )

    private fun importingBook() =
        BookEntity(
            id = BOOK_ID,
            title = "livro",
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

    /**
     * Reads every page at once, except that, when [blocking], it waits on each page after the first until the test
     * lets it finish or the import is cancelled (which interrupts the wait, as ML Kit's wait is).
     */
    private class SteppedRecognizer(
        private val blocking: Boolean = true,
    ) : PageTextRecognizer {
        val requested = mutableListOf<Int>()
        private val reading = List(PAGES) { CountDownLatch(1) }
        private val finish = List(PAGES) { CountDownLatch(1) }
        private var current = 0

        fun reading(page: Int) {
            assertThat(reading[page].await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            current = page
        }

        fun finishPage() = finish[current].countDown()

        override fun open(source: File): PageTextRecognizer.Session =
            object : PageTextRecognizer.Session {
                override fun recognize(pageIndex: Int): PageLayout {
                    requested += pageIndex
                    reading[pageIndex].countDown()
                    if (blocking && pageIndex > 0) finish[pageIndex].await()
                    return scannedPage(pageIndex) { paragraph("Página $pageIndex do livro, lida com cuidado e calma.") }
                }

                override fun close() = Unit
            }
    }

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
        const val PAGES = 3
        const val WAIT_SECONDS = 10L
    }
}
