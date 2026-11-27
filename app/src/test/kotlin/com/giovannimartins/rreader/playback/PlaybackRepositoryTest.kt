package com.giovannimartins.rreader.playback

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ChapterEntity
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.db.ReadingPositionEntity
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folder = TemporaryFolder().apply { create() }
    private val clock = Clock.fixed(Instant.ofEpochMilli(42_000), ZoneOffset.UTC)
    private val texts = listOf("Primeiro capítulo do livro.", "Segundo capítulo, um pouco maior que o primeiro.")
    private lateinit var database: RReaderDatabase
    private lateinit var files: BookFileStore
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        files = BookFileStore(File(folder.root, "files"))
    }

    @After
    fun tearDown() {
        database.close()
        folder.delete()
    }

    @Test
    fun `loads a ready book with its chapters, saved position, voice and speed`() =
        runTest {
            insertBook(hasCover = true)
            database.readingPositionDao().save(ReadingPositionEntity(BOOK_ID, 1, 7, updatedAt = 1))
            val repository = repository()
            settings.setPlaybackSpeed(1.25f)

            val loaded = (repository.load(BOOK_ID) as BookLoad.Loaded).book

            assertThat(loaded.title).isEqualTo("Iracema")
            assertThat(loaded.authors).containsExactly("José de Alencar")
            assertThat(loaded.chapters.map { it.length }).containsExactly(texts[0].length, texts[1].length).inOrder()
            assertThat(loaded.chapters.map { it.title }).containsExactly("I", "II").inOrder()
            assertThat(loaded.position).isEqualTo(ReadingPosition(1, 7))
            assertThat(loaded.voice).isEqualTo(voice())
            assertThat(loaded.speed).isEqualTo(1.25f)
            assertThat(loaded.cover).isEqualTo(files.coverFile(BOOK_ID))
            assertThat(loaded.pausedFor).isEqualTo((42_000 - 1).milliseconds)
            assertThat(repository.chapterText(BOOK_ID, 1)).isEqualTo(texts[1])
        }

    @Test
    fun `a book never listened to has no pause to resume from`() =
        runTest {
            insertBook()

            val loaded = (repository().load(BOOK_ID) as BookLoad.Loaded).book

            assertThat(loaded.position).isEqualTo(ReadingPosition.START)
            assertThat(loaded.pausedFor).isNull()
        }

    @Test
    fun `books that are missing or not ready are unavailable`() =
        runTest {
            insertBook(status = BookStatus.IMPORTING)
            val repository = repository()

            assertThat(repository.load(BOOK_ID)).isEqualTo(BookLoad.Unavailable)
            assertThat(repository.load("00000000-0000-0000-0000-00000000000f")).isEqualTo(BookLoad.Unavailable)
        }

    @Test
    fun `saved positions are kept inside the book, and a finished book starts over`() =
        runTest {
            val chapters = texts.map { PlaybackChapter(null, it.length) }

            fun resume(
                chapter: Int,
                offset: Int,
            ) = PlaybackRepository.resumePosition(ReadingPositionEntity(BOOK_ID, chapter, offset, 0), chapters)

            assertThat(PlaybackRepository.resumePosition(null, chapters)).isEqualTo(ReadingPosition.START)
            assertThat(resume(0, 500)).isEqualTo(ReadingPosition(0, texts[0].length))
            assertThat(resume(7, 3)).isEqualTo(ReadingPosition.START)
            assertThat(resume(1, texts[1].length)).isEqualTo(ReadingPosition.START)
        }

    @Test
    fun `saves the position with the time and the speed in the settings`() =
        runTest {
            insertBook()
            val repository = repository()

            repository.savePosition(BOOK_ID, ReadingPosition(1, 12))
            repository.saveSpeed(1.5f)

            val saved = database.readingPositionDao().find(BOOK_ID)
            assertThat(saved).isEqualTo(ReadingPositionEntity(BOOK_ID, 1, 12, updatedAt = 42_000))
            assertThat(settings.settings.first().playbackSpeed).isEqualTo(1.5f)
        }

    @Test
    fun `saving the position of a removed book is ignored`() =
        runTest {
            repository().savePosition(BOOK_ID, ReadingPosition(0, 1))

            assertThat(database.readingPositionDao().find(BOOK_ID)).isNull()
        }

    private fun TestScope.repository(): PlaybackRepository {
        settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                ) { File(folder.root, "settings.preferences_pb") },
            )
        return PlaybackRepository(
            bookDao = database.bookDao(),
            positionDao = database.readingPositionDao(),
            files = files,
            preferences = PlaybackPreferences(settings, FakeSpeechEngine()),
            clock = clock,
            io = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private suspend fun insertBook(
        status: BookStatus = BookStatus.READY,
        hasCover: Boolean = false,
    ) {
        database.bookDao().insert(
            BookEntity(
                id = BOOK_ID,
                title = "Iracema",
                authors = listOf("José de Alencar"),
                language = "pt-BR",
                description = null,
                format = "EPUB",
                sourceSha256 = "a",
                sourceSize = 1,
                status = status,
                failureReason = null,
                totalChars = texts.sumOf { it.length }.toLong(),
                chapterCount = texts.size,
                hasCover = hasCover,
                addedAt = 1,
                lastOpenedAt = null,
                archived = false,
                voiceId = null,
            ),
        )
        val titles = listOf("I", "II")
        database.bookDao().insertChapters(
            texts.mapIndexed {
                index,
                text,
                ->
                ChapterEntity(BOOK_ID, index, titles[index], text.length)
            },
        )
        files.writeChapters(BOOK_ID, texts.map { Chapter(null, it) })
        if (hasCover) files.writeCover(BOOK_ID, CoverImage("image/jpeg", byteArrayOf(1, 2, 3)))
    }
}
