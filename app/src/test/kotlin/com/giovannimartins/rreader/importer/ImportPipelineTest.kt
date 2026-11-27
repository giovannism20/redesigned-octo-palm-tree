package com.giovannimartins.rreader.importer

import android.content.Context
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ChapterEntity
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.pdf.NativeCallMarkers
import com.giovannimartins.rreader.pdf.PageTextRecognizer
import com.giovannimartins.rreader.pdf.PdfFixture
import com.giovannimartins.rreader.pdf.PdfLayoutExtractor
import com.giovannimartins.rreader.pdf.ScriptedRecognizer
import com.giovannimartins.rreader.pdf.pdfOcr
import com.giovannimartins.rreader.pdf.scannedPage
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.layout.LayoutBookBuilder
import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ImportPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folder = TemporaryFolder().apply { create() }
    private lateinit var database: RReaderDatabase
    private lateinit var files: BookFileStore
    private lateinit var jobs: RecordingImportJobs

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        files = BookFileStore(folder.root)
        jobs = RecordingImportJobs()
    }

    @After
    fun tearDown() {
        database.close()
        folder.delete()
    }

    @Test
    fun `queues an EPUB, stores its source and rejects the same file twice`() =
        runTest {
            val intake = intake()

            val first = intake.importStream(ByteArrayInputStream(EPUB_HEADER), "meu_livro")
            val second = intake.importStream(ByteArrayInputStream(EPUB_HEADER), "copy")

            val bookId = (first as IntakeResult.Queued).bookId
            assertThat(second).isEqualTo(IntakeResult.Duplicate(bookId))
            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.IMPORTING)
            assertThat(stored.title).isEqualTo("meu_livro")
            assertThat(files.sourceFile(bookId, BookFormat.EPUB).readBytes()).isEqualTo(EPUB_HEADER)
            assertThat(jobs.enqueued).containsExactly(bookId)
        }

    @Test
    fun `SR-13 a file name of any length becomes a title within the title limit`() =
        runTest {
            val result = intake().importStream(ByteArrayInputStream(EPUB_HEADER), "livro ".repeat(100_000))

            val stored = database.bookDao().findById((result as IntakeResult.Queued).bookId)!!
            assertThat(stored.title).hasLength(MetadataLimits.MAX_TITLE_LENGTH)
            assertThat(stored.title).startsWith("livro livro")
        }

    @Test
    fun `importing a file that failed before prepares it again`() =
        runTest {
            val bookId = (intake().importStream(ByteArrayInputStream(EPUB_HEADER), "x") as IntakeResult.Queued).bookId
            val unsupported = BookParser { ParseOutcome.Failed(ImportFailure.FORMAT_NOT_YET_SUPPORTED) }
            processor(unsupported).process(bookId)

            val again = intake().importStream(ByteArrayInputStream(EPUB_HEADER), "x")

            assertThat(again).isEqualTo(IntakeResult.Queued(bookId))
            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.IMPORTING)
            assertThat(stored.failureReason).isNull()
        }

    @Test
    fun `rejects content that is neither PDF nor EPUB`() =
        runTest {
            val result = intake().importStream(ByteArrayInputStream("just text".toByteArray()), "notes")

            assertThat(result).isEqualTo(IntakeResult.Rejected(ImportFailure.UNSUPPORTED_FORMAT))
        }

    @Test
    fun `processor persists chapters, cover and metadata`() =
        runTest {
            val bookId = (intake().importStream(ByteArrayInputStream(EPUB_HEADER), "x") as IntakeResult.Queued).bookId
            val parsed =
                NormalizedBook(
                    BookMetadata("Iracema", listOf("José de Alencar"), language = "pt-BR"),
                    listOf(Chapter("I", "Verdes mares bravios."), Chapter("II", "Onde vai a afouta jangada?")),
                    CoverImage("image/jpeg", byteArrayOf(1, 2, 3)),
                )

            processor(BookParser { ParseOutcome.Parsed(parsed) }).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.READY)
            assertThat(stored.title).isEqualTo("Iracema")
            assertThat(stored.totalChars).isEqualTo(parsed.totalLength)
            assertThat(stored.hasCover).isTrue()
            assertThat(files.readChapterText(bookId, 1)).isEqualTo("Onde vai a afouta jangada?")
            assertThat(files.coverFile(bookId).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        }

    @Test
    fun `processor records parser failures on the book`() =
        runTest {
            val bookId = (intake().importStream(ByteArrayInputStream(EPUB_HEADER), "x") as IntakeResult.Queued).bookId

            processor(BookParser { ParseOutcome.Failed(ImportFailure.DRM_PROTECTED) }).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.FAILED)
            assertThat(stored.failureReason).isEqualTo(ImportFailure.DRM_PROTECTED)
        }

    @Test
    fun `an error that escapes a parser marks the book failed instead of leaving it importing`() =
        runTest {
            assertThat(failureWhenParserThrows(IllegalStateException("parser bug"))).isEqualTo(ImportFailure.CORRUPTED)
            assertThat(failureWhenParserThrows(StackOverflowError())).isEqualTo(ImportFailure.TOO_COMPLEX)
            assertThat(failureWhenParserThrows(OutOfMemoryError())).isEqualTo(ImportFailure.TOO_COMPLEX)
        }

    @Test
    fun `a database error while storing a parsed book marks it failed as a storage error`() =
        runTest {
            val bookId = (intake().importStream(ByteArrayInputStream(EPUB_HEADER), "x") as IntakeResult.Queued).bookId
            val storage = database.bookDao()
            val brokenStorage =
                object : BookDao by storage {
                    override suspend fun completeImport(
                        book: BookEntity,
                        chapters: List<ChapterEntity>,
                    ): Unit = throw SQLiteFullException("database or disk is full")
                }
            val parsed = NormalizedBook(BookMetadata("Iracema"), listOf(Chapter(null, "Verdes mares bravios.")))

            processor(BookParser { ParseOutcome.Parsed(parsed) }, dao = brokenStorage).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.FAILED)
            assertThat(stored.failureReason).isEqualTo(ImportFailure.STORAGE_ERROR)
        }

    @Test
    fun `imports a real EPUB end to end`() =
        runTest {
            val bookId =
                (
                    intake().importStream(
                        ByteArrayInputStream(MinimalEpub.bytes()),
                        "iracema",
                    ) as IntakeResult.Queued
                ).bookId

            processor(EpubBookParser()).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.READY)
            assertThat(stored.title).isEqualTo("Iracema")
            assertThat(stored.language).isEqualTo("pt-BR")
            assertThat(database.bookDao().chapters(bookId).map { it.title }).containsExactly("I", "II").inOrder()
            assertThat(files.readChapterText(bookId, 0)).contains("Verdes mares bravios")
        }

    @Test
    fun `imports a real PDF end to end`() =
        runTest {
            val pdf =
                PdfFixture(context).apply {
                    title = "Iracema"
                    page {
                        line("Verdes mares bravios de minha terra natal,", x = PdfFixture.INDENT)
                        line("onde canta a jandaia nas frondes da carnaúba.")
                    }
                }
            val bookId =
                (
                    intake().importStream(
                        ByteArrayInputStream(pdf.bytes()),
                        "iracema",
                    ) as IntakeResult.Queued
                ).bookId
            processor(pdfParser(), BookFormat.PDF).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.format).isEqualTo(BookFormat.PDF.name)
            assertThat(stored.status).isEqualTo(BookStatus.READY)
            assertThat(stored.title).isEqualTo("Iracema")
            assertThat(files.readChapterText(bookId, 0))
                .isEqualTo("Verdes mares bravios de minha terra natal, onde canta a jandaia nas frondes da carnaúba.")
        }

    @Test
    fun `imports a scanned PDF end to end, reporting progress, then drops the recognized pages`() =
        runTest {
            val scan = PdfFixture(context).apply { repeat(2) { picturePage() } }
            val bookId = (intake().importStream(ByteArrayInputStream(scan.bytes()), "x") as IntakeResult.Queued).bookId
            val recognizer =
                ScriptedRecognizer(
                    listOf(
                        scannedPage(0) {
                            paragraph("Verdes mares bravios de minha terra natal,", "onde canta a jandaia")
                        },
                        scannedPage(1) {
                            paragraph("nas frondes da carnaúba, verdes mares que brilhais.", continued = true)
                        },
                    ),
                )
            val progress = mutableListOf<ImportProgress>()

            processor(pdfParser(recognizer), BookFormat.PDF).process(bookId, progress::add)

            assertThat(database.bookDao().findById(bookId)!!.status).isEqualTo(BookStatus.READY)
            assertThat(files.readChapterText(bookId, 0))
                .isEqualTo(
                    "Verdes mares bravios de minha terra natal, onde canta a jandaia nas frondes da carnaúba, " +
                        "verdes mares que brilhais.",
                )
            assertThat(progress)
                .containsExactly(ImportProgress.RecognizingText(1, 2), ImportProgress.RecognizingText(2, 2))
                .inOrder()
            assertThat(files.ocrPageFile(bookId, 0).parentFile!!.exists()).isFalse()
        }

    @Test
    fun `a scanned PDF where nothing readable is recognized fails clearly and drops its pages`() =
        runTest {
            val scan = PdfFixture(context).apply { repeat(2) { picturePage() } }
            val bookId = (intake().importStream(ByteArrayInputStream(scan.bytes()), "x") as IntakeResult.Queued).bookId

            processor(pdfParser(ScriptedRecognizer()), BookFormat.PDF).process(bookId)

            val stored = database.bookDao().findById(bookId)!!
            assertThat(stored.status).isEqualTo(BookStatus.FAILED)
            assertThat(stored.failureReason).isEqualTo(ImportFailure.SCANNED_PDF)
            assertThat(files.ocrPageFile(bookId, 0).parentFile!!.exists()).isFalse()
        }

    /** Imports a new file with a parser that throws [error]; returns why the book failed, after checking it did. */
    private suspend fun kotlinx.coroutines.test.TestScope.failureWhenParserThrows(error: Throwable): ImportFailure? {
        val content = EPUB_HEADER + error.javaClass.name.toByteArray()
        val bookId = (intake().importStream(ByteArrayInputStream(content), "x") as IntakeResult.Queued).bookId

        processor(BookParser { throw error }).process(bookId)

        val stored = database.bookDao().findById(bookId)!!
        assertThat(stored.status).isEqualTo(BookStatus.FAILED)
        return stored.failureReason
    }

    private fun pdfParser(recognizer: PageTextRecognizer = ScriptedRecognizer()) =
        PdfBookParser(
            PdfLayoutExtractor(context),
            pdfOcr(recognizer, folder.root),
            { null },
            LayoutBookBuilder(),
            NativeCallMarkers(files),
        )

    private fun kotlinx.coroutines.test.TestScope.intake() =
        BookIntake(
            contentResolver = context.contentResolver,
            bookDao = database.bookDao(),
            files = files,
            jobs = jobs,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            io = StandardTestDispatcher(testScheduler),
        )

    private fun kotlinx.coroutines.test.TestScope.processor(
        parser: BookParser,
        format: BookFormat = BookFormat.EPUB,
        dao: BookDao = database.bookDao(),
    ) = BookImportProcessor(
        bookDao = dao,
        files = files,
        parsers = mapOf(format to parser),
        io = StandardTestDispatcher(testScheduler),
        cpu = StandardTestDispatcher(testScheduler),
    )

    private companion object {
        /** Minimal bytes that the format detector recognises as an EPUB (stored "mimetype" first entry). */
        val EPUB_HEADER: ByteArray =
            byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(4) + byteArrayOf(0, 0) + ByteArray(8) +
                intLe(20) + intLe(20) + shortLe(8) + shortLe(0) +
                "mimetype".toByteArray() + "application/epub+zip".toByteArray()

        fun intLe(value: Int) =
            byteArrayOf(
                value.toByte(),
                (value shr 8).toByte(),
                (value shr 16).toByte(),
                (
                    value shr
                        24
                ).toByte(),
            )

        fun shortLe(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())
    }
}
