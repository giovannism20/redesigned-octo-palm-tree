package com.giovannimartins.rreader.ui.player

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlinx.coroutines.flow.StateFlow

/** The UI's handle on playback, implemented by [PlaybackConnection]. Called on the main thread. */
interface PlaybackControls {
    /** The book open in the player, or null when none is. */
    val nowPlaying: StateFlow<NowPlaying?>

    /** Opens [bookId] at its saved position, or resumes it when it is already open, and starts listening. */
    fun play(bookId: String)

    /** Starts listening again: after a pause, a stop, a problem or the end of the book (from the start). */
    fun resume()

    fun pause()

    fun seekBack()

    fun seekForward()

    /** Moves to [positionMs] in the current chapter. */
    fun seekTo(positionMs: Long)

    /** Jumps to the start of chapter [index] and starts listening. */
    fun playChapter(index: Int)

    fun setSpeed(speed: Float)

    /** Starts listening from the start of the segment that holds [position] in the open book. */
    fun readFrom(position: ReadingPosition)
}
