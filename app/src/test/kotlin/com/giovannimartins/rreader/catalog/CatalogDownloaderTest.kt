package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.EPUB
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ok
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.redirect
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.status
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.importer.BookImportProcessor
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.EpubBookParser
import com.giovannimartins.rreader.importer.FakeImportJobs
import com.giovannimartins.rreader.importer.MinimalEpub
import com.giovannimartins.rreader.textkit.BookFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A downloaded book goes through the same intake and import as any file the listener adds. */
@RunWith(RobolectricTestRunner::class)
class CatalogDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val transport = FakeTransport()
    private val jobs = FakeImportJobs()
    private lateinit var database: RReaderDatabase
    private lateinit var files: BookFileStore
    private lateinit var downloader: CatalogDownloader

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        files = BookFileStore(folder.root)
        val intake =
            BookIntake(
                contentResolver = context.contentResolver,
                bookDao = database.bookDao(),
                files = files,
                jobs = jobs,
                clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                io = Dispatchers.Unconfined,
            )
        downloader =
            CatalogDownloader(CatalogHttp(transport, Dispatchers.Unconfined), intake, files, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `downloads the EPUB edition and queues its import, named until its own title is read`() {
        val epub = MinimalEpub.bytes()
        serve(epub)

        val outcome = download()

        val bookId = (outcome as DownloadOutcome.InLibrary).bookId
        assertThat(outcome.alreadyThere).isFalse()
        val book = runBlocking { database.bookDao().findById(bookId) }!!
        assertThat(book.title).isEqualTo("Dom Casmurro")
        assertThat(book.status).isEqualTo(BookStatus.IMPORTING)
        assertThat(files.sourceFile(bookId, BookFormat.EPUB).readBytes()).isEqualTo(epub)
        assertThat(jobs.enqueued).containsExactly(bookId)
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK, FILE).inOrder()
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `a book downloaded again is already in the library`() {
        serve(MinimalEpub.bytes())
        val first = download() as DownloadOutcome.InLibrary

        val second = download()

        assertThat(second).isEqualTo(DownloadOutcome.InLibrary(first.bookId, alreadyThere = true))
        assertThat(jobs.enqueued).containsExactly(first.bookId)
    }

    @Test
    fun `a downloaded Gutenberg book is prepared without its license`() {
        val gutenbergBook =
            MinimalEpub.bytes(
                title = "Dom Casmurro",
                author = "Machado de Assis",
                chapters =
                    listOf(
                        "I" to "*** START OF THE PROJECT GUTENBERG EBOOK DOM CASMURRO *** Uma noite destas.",
                        "II" to "Não consultes diccionarios. *** END OF THE PROJECT GUTENBERG EBOOK DOM CASMURRO ***",
                    ),
            )
        serve(gutenbergBook)
        val bookId = (download() as DownloadOutcome.InLibrary).bookId

        runBlocking { processor().process(bookId) }

        val book = runBlocking { database.bookDao().findById(bookId) }!!
        assertThat(book.status).isEqualTo(BookStatus.READY)
        assertThat(book.authors).containsExactly("Machado de Assis")
        assertThat(files.readChapterText(bookId, 0)).isEqualTo("Uma noite destas.")
        assertThat(files.readChapterText(bookId, 1)).isEqualTo("II\n\nNão consultes diccionarios.")
    }

    @Test
    fun `a file that is not an EPUB is refused by the import's signature check`() {
        serve("<html><body>Captcha</body></html>".toByteArray())

        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.NOT_A_BOOK, retryable = false))
        assertThat(jobs.enqueued).isEmpty()
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `a book larger than the download limit is not downloaded`() {
        val body = FakeTransport.Companion.CountingBody(1)
        transport.answer(BOOK) { ok(body, EPUB, contentLength = CatalogDownloader.MAX_DOWNLOAD_BYTES + 1) }

        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.TOO_LARGE, retryable = false))
        assertThat(body.read).isEqualTo(0)
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `an answer that is not an EPUB, or that leads off the allowlist, is not used`() {
        transport.answer(BOOK) { ok("<html>Down for maintenance</html>".toByteArray(), "text/html") }
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.UNAVAILABLE, retryable = false))

        transport.answer(BOOK) { redirect("https://mirror.example/pg55752-images-3.epub") }
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.UNAVAILABLE, retryable = false))
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK, BOOK)
    }

    @Test
    fun `network trouble and a site that is down may pass, a missing book will not`() {
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.NETWORK, retryable = true))

        transport.answer(BOOK) { status(503) }
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.SITE_DOWN, retryable = true))

        transport.answer(BOOK) { status(404) }
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.NOT_FOUND, retryable = false))
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `downloads from the official mirror when the site is down, busy or out of reach`() {
        transport.answer(MIRROR) { ok(MinimalEpub.bytes(), OCTET_STREAM) }
        val siteTroubles: List<() -> HttpResponse> =
            listOf({ status(503) }, { status(429) }, { throw SocketTimeoutException("connect timed out") })

        siteTroubles.forEach { trouble ->
            transport.requests.clear()
            transport.answer(BOOK, trouble)

            assertThat(download()).isInstanceOf(DownloadOutcome.InLibrary::class.java)
            assertThat(transport.requests.map { it.address }).containsExactly(BOOK, MIRROR).inOrder()
        }
        assertThat(jobs.enqueued).hasSize(1)
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `does not try the mirror when the site answered for the book`() {
        transport.answer(MIRROR) { ok(MinimalEpub.bytes(), OCTET_STREAM) }
        transport.answer(BOOK) { status(404) }

        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.NOT_FOUND, retryable = false))
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK)
    }

    @Test
    fun `reports the site down when neither the site nor the mirror delivers the book`() {
        transport.answer(BOOK) { status(503) }
        transport.answer(MIRROR) { status(404) }

        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.SITE_DOWN, retryable = true))
        assertThat(temporaryFiles()).isEmpty()
    }

    @Test
    fun `accepts an EPUB served as a generic binary file, which the intake still checks`() {
        transport.answer(BOOK) { ok(MinimalEpub.bytes(), OCTET_STREAM) }
        assertThat(download()).isInstanceOf(DownloadOutcome.InLibrary::class.java)

        transport.answer(BOOK) { ok("<html>Down for maintenance</html>".toByteArray(), OCTET_STREAM) }
        assertThat(download()).isEqualTo(DownloadOutcome.Failed(DownloadFailure.NOT_A_BOOK, retryable = false))
    }

    @Test
    fun `reports the download's progress`() {
        val reports = mutableListOf<Pair<Long, Long>>()
        val epub = MinimalEpub.bytes()
        serve(epub)

        runBlocking {
            downloader.download(
                DOM_CASMURRO.id,
                DOM_CASMURRO.title,
            ) { read, total -> reports += read to total }
        }

        assertThat(reports.last()).isEqualTo(epub.size.toLong() to epub.size.toLong())
    }

    /** Serves [bytes] as ebook 55752's EPUB, behind the redirect Gutenberg answers with. */
    private fun serve(bytes: ByteArray) {
        transport.answer(BOOK) { redirect("/cache/epub/55752/pg55752-images-3.epub") }
        transport.answer(FILE) { ok(bytes, EPUB) }
    }

    private fun download(): DownloadOutcome = runBlocking { downloader.download(DOM_CASMURRO.id, DOM_CASMURRO.title) }

    private fun temporaryFiles(): List<File> = File(folder.root, "imports").listFiles().orEmpty().toList()

    private fun processor() =
        BookImportProcessor(
            bookDao = database.bookDao(),
            files = files,
            parsers = mapOf(BookFormat.EPUB to EpubBookParser()),
            io = Dispatchers.Unconfined,
            cpu = Dispatchers.Unconfined,
        )

    private companion object {
        const val BOOK = "https://www.gutenberg.org/ebooks/55752.epub3.images"
        const val FILE = "https://www.gutenberg.org/cache/epub/55752/pg55752-images-3.epub"
        const val MIRROR = "https://gutenberg.pglaf.org/cache/epub/55752/pg55752-images-3.epub"
        const val OCTET_STREAM = "application/octet-stream"
    }
}
