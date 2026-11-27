package com.giovannimartins.rreader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ChapterEntity
import com.giovannimartins.rreader.data.db.LibraryRow
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.db.ReadingPositionEntity
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookDaoTest {
    private lateinit var database: RReaderDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RReaderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `library reports characters read across chapters`() =
        runTest {
            dao.insert(book("00000000-0000-0000-0000-000000000001", addedAt = 1))
            dao.insertChapters(listOf(chapter(0, 100), chapter(1, 50), chapter(2, 70)))
            database.readingPositionDao().save(
                ReadingPositionEntity(BOOK_ID, chapterIndex = 2, charOffset = 10, updatedAt = 5),
            )

            val row = dao.observeLibrary().first().single()

            assertThat(row.charsRead).isEqualTo(160)
        }

    @Test
    fun `SR-13 the library reads a book whose stored title and description are far too large`() =
        runTest {
            val huge = "Memórias ".repeat(400_000)
            dao.insert(
                book(BOOK_ID).copy(
                    title = huge,
                    authors = List(5_000) { "Autor $it" },
                    description = huge,
                    status = BookStatus.READY,
                ),
            )

            val row = dao.observeLibrary().first().single()

            assertThat(row.id).isEqualTo(BOOK_ID)
            assertThat(row.title).hasLength(MetadataLimits.MAX_TITLE_LENGTH)
            assertThat(huge).startsWith(row.title)
            assertThat(row.authors.joinToString("|").length).isAtMost(LibraryRow.MAX_AUTHORS_COLUMN_LENGTH)
            assertThat(row.status).isEqualTo(BookStatus.READY)
        }

    @Test
    fun `library lists recent activity first and hides archived books`() =
        runTest {
            dao.insert(book(BOOK_ID, addedAt = 1))
            dao.insert(book(OTHER_ID, addedAt = 2, sha = "b"))
            dao.insert(book(ARCHIVED_ID, addedAt = 3, sha = "c").copy(archived = true))
            database.readingPositionDao().save(ReadingPositionEntity(BOOK_ID, 0, 0, updatedAt = 10))

            val ids = dao.observeLibrary().first().map { it.id }

            assertThat(ids).containsExactly(BOOK_ID, OTHER_ID).inOrder()
        }

    @Test
    fun `deleting a book cascades to chapters and position`() =
        runTest {
            dao.insert(book(BOOK_ID))
            dao.insertChapters(listOf(chapter(0, 10)))
            database.readingPositionDao().save(ReadingPositionEntity(BOOK_ID, 0, 3, updatedAt = 1))

            dao.delete(BOOK_ID)

            assertThat(dao.chapters(BOOK_ID)).isEmpty()
            assertThat(database.readingPositionDao().find(BOOK_ID)).isNull()
        }

    @Test
    fun `completing an import stores chapters and metadata`() =
        runTest {
            val imported = book(BOOK_ID)
            dao.insert(imported)

            dao.completeImport(
                imported.copy(title = "Dom Casmurro", authors = listOf("Machado de Assis"), totalChars = 30),
                listOf(chapter(0, 10), chapter(1, 20)),
            )

            val stored = dao.findById(BOOK_ID)!!
            assertThat(stored.status).isEqualTo(BookStatus.READY)
            assertThat(stored.title).isEqualTo("Dom Casmurro")
            assertThat(stored.authors).containsExactly("Machado de Assis")
            assertThat(stored.chapterCount).isEqualTo(2)
            assertThat(dao.chapters(BOOK_ID).map { it.charCount }).containsExactly(10, 20).inOrder()
        }

    private fun book(
        id: String,
        addedAt: Long = 0,
        sha: String = "a",
    ) = BookEntity(
        id = id,
        title = "",
        authors = emptyList(),
        language = null,
        description = null,
        format = "EPUB",
        sourceSha256 = sha,
        sourceSize = 1,
        status = BookStatus.IMPORTING,
        failureReason = null,
        totalChars = 0,
        chapterCount = 0,
        hasCover = false,
        addedAt = addedAt,
        lastOpenedAt = null,
        archived = false,
        voiceId = null,
    )

    private fun chapter(
        index: Int,
        length: Int,
    ) = ChapterEntity(BOOK_ID, index, title = null, charCount = length)

    private companion object {
        const val BOOK_ID = "00000000-0000-0000-0000-000000000001"
        const val OTHER_ID = "00000000-0000-0000-0000-000000000002"
        const val ARCHIVED_ID = "00000000-0000-0000-0000-000000000003"
    }
}
