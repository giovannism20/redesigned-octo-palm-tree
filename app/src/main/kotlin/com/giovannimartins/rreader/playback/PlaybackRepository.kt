package com.giovannimartins.rreader.playback

import android.database.sqlite.SQLiteException
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ReadingPositionDao
import com.giovannimartins.rreader.data.db.ReadingPositionEntity
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * [PlaybackContent] backed by the library database, the book files and the listener's preferences. Loading a
 * book first restores the speaking rates learned in earlier sessions, so its chapter durations use them.
 */
@Singleton
class PlaybackRepository
    @Inject
    constructor(
        private val bookDao: BookDao,
        private val positionDao: ReadingPositionDao,
        private val files: BookFileStore,
        private val preferences: PlaybackPreferences,
        private val rates: SpeakingRateTracker,
        private val clock: Clock,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : PlaybackContent {
        override suspend fun load(bookId: String): BookLoad =
            try {
                rates.restore()
                withContext(io) { loadReadyBook(bookId) }
            } catch (e: SQLiteException) {
                Diagnostics.recordHandled("load book for playback", e)
                BookLoad.Unavailable
            } catch (e: IOException) {
                // The settings could not be read.
                Diagnostics.recordHandled("load book for playback", e)
                BookLoad.Unavailable
            }

        override suspend fun chapterText(
            bookId: String,
            chapterIndex: Int,
        ): String = withContext(io) { files.readChapterText(bookId, chapterIndex) }

        override suspend fun savePosition(
            bookId: String,
            position: ReadingPosition,
        ) {
            val entity = ReadingPositionEntity(bookId, position.chapterIndex, position.charOffset, clock.millis())
            try {
                withContext(io) { positionDao.save(entity) }
            } catch (e: SQLiteException) {
                // For example, the book was removed while it was playing.
                Diagnostics.recordHandled("save reading position", e)
            }
        }

        override suspend fun saveSpeed(speed: Float) {
            try {
                preferences.saveSpeed(speed)
            } catch (e: IOException) {
                Diagnostics.recordHandled("save playback speed", e)
            }
        }

        private suspend fun loadReadyBook(bookId: String): BookLoad {
            val book = bookDao.findById(bookId)?.takeIf { it.status == BookStatus.READY }
            val chapters =
                book
                    ?.let {
                        bookDao.chapters(
                            it.id,
                        )
                    }.orEmpty()
                    .map { PlaybackChapter(it.title, it.charCount) }
            val saved = book?.let { positionDao.find(it.id) }
            return if (book == null || chapters.isEmpty()) {
                BookLoad.Unavailable
            } else {
                BookLoad.Loaded(
                    PlaybackBook(
                        id = book.id,
                        title = book.title,
                        authors = book.authors,
                        cover = files.coverFile(book.id).takeIf { book.hasCover && it.isFile },
                        chapters = chapters,
                        voice = preferences.voiceFor(book.voiceId),
                        position = resumePosition(saved, chapters),
                        speed = preferences.speed(),
                        pausedFor = saved?.let { (clock.millis() - it.updatedAt).coerceAtLeast(0).milliseconds },
                    ),
                )
            }
        }

        internal companion object {
            /**
             * Where a book resumes: the saved position kept inside the book, or the beginning when nothing was
             * saved or the book was listened to the end.
             */
            fun resumePosition(
                saved: ReadingPositionEntity?,
                chapters: List<PlaybackChapter>,
            ): ReadingPosition {
                val chapter = saved?.let { chapters.getOrNull(it.chapterIndex) }
                val position =
                    if (saved == null || chapter == null) {
                        ReadingPosition.START
                    } else {
                        ReadingPosition(saved.chapterIndex, saved.charOffset.coerceIn(0, chapter.length))
                    }
                val finished = ReadingEstimates.remainingCharacters(position, chapters.map { it.length }) <= 0
                return if (finished) ReadingPosition.START else position
            }
        }
    }
