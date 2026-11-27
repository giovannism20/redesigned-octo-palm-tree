package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition

/** Result of [PlaybackContent.load]. */
sealed interface BookLoad {
    data class Loaded(
        val book: PlaybackBook,
    ) : BookLoad

    /** The book does not exist, is not ready to be read or cannot be read from storage. */
    data object Unavailable : BookLoad
}

/** Storage the player reads books from and saves listening state to. */
interface PlaybackContent {
    /** Loads a book with its chapters, saved position, voice and speed. */
    suspend fun load(bookId: String): BookLoad

    /**
     * The normalized text of chapter [chapterIndex].
     *
     * @throws java.io.IOException when the chapter file cannot be read.
     */
    suspend fun chapterText(
        bookId: String,
        chapterIndex: Int,
    ): String

    suspend fun savePosition(
        bookId: String,
        position: ReadingPosition,
    )

    suspend fun saveSpeed(speed: Float)
}
