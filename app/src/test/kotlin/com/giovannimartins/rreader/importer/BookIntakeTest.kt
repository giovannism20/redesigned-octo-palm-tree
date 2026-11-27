package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The intake against misbehaving providers and two intakes of the same file at once. */
@RunWith(RobolectricTestRunner::class)
class BookIntakeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RReaderDatabase
    private lateinit var provider: HostileProvider
    private val files by lazy { BookFileStore(folder.root) }
    private val jobs = RecordingImportJobs()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        provider = Robolectric.setupContentProvider(HostileProvider::class.java, HostileProvider.AUTHORITY)
        provider.content = MinimalEpub.bytes()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a shared file is received from its provider`() =
        runBlocking<Unit> {
            val result = intake().import(HostileProvider.BOOK)

            val bookId = (result as IntakeResult.Queued).bookId
            assertThat(database.bookDao().findById(bookId)!!.title).isEqualTo("livro")
        }

    @Test
    fun `SR-7 a provider that throws when its file is opened or its name is asked for is a storage error`() =
        runBlocking<Unit> {
            HostileProvider.Failure.entries.filter { it != HostileProvider.Failure.NONE }.forEach { failure ->
                provider.failure = failure

                assertThat(intake().import(HostileProvider.BOOK))
                    .isEqualTo(IntakeResult.Rejected(ImportFailure.STORAGE_ERROR))
            }
            assertThat(bookFolders()).isEmpty()
        }

    @Test
    fun `the same file received twice at once is added once`() =
        runBlocking<Unit> {
            val intake = intake()
            val epub = MinimalEpub.bytes()

            val results =
                List(2) { async(Dispatchers.IO) { intake.importStream(ByteArrayInputStream(epub), "livro") } }
                    .awaitAll()

            val added = results.filterIsInstance<IntakeResult.Queued>().single()
            assertThat(results).contains(IntakeResult.Duplicate(added.bookId))
            assertThat(bookFolders()).containsExactly(added.bookId)
        }

    @Test
    fun `a file another intake added meanwhile is a duplicate, and its copy is deleted`() =
        runBlocking<Unit> {
            val epub = MinimalEpub.bytes()
            val first = intake().importStream(ByteArrayInputStream(epub), "livro") as IntakeResult.Queued
            val dao = database.bookDao()
            // The other intake's row arrives between this intake's duplicate check and its insert.
            val lateToSeeIt =
                object : BookDao by dao {
                    private var checks = 0

                    override suspend fun findBySourceSha256(sha256: String): BookEntity? =
                        if (checks++ == 0) null else dao.findBySourceSha256(sha256)
                }

            val second = intake(lateToSeeIt).importStream(ByteArrayInputStream(epub), "livro")

            assertThat(second).isEqualTo(IntakeResult.Duplicate(first.bookId))
            assertThat(bookFolders()).containsExactly(first.bookId)
            assertThat(jobs.enqueued).containsExactly(first.bookId)
        }

    private fun bookFolders(): List<String> = File(folder.root, "books").list().orEmpty().toList()

    private fun intake(dao: BookDao = database.bookDao()) =
        BookIntake(
            contentResolver = context.contentResolver,
            bookDao = dao,
            files = files,
            jobs = jobs,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            io = Dispatchers.IO,
        )
}
