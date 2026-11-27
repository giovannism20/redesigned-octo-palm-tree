package com.giovannimartins.rreader.ui.player

import java.io.File

/** Why listening stopped, as the player explains it. */
enum class PlaybackProblem {
    /** No usable voice: the listener can install one from the Voices tab. */
    NO_VOICE,
    BOOK_UNAVAILABLE,
    SPEECH_FAILED,
    OTHER,
}

/** The book open in the player and how listening is going, as the mini and full players show it. */
data class NowPlaying(
    val bookId: String,
    /** Empty when unknown, or while the book is opening. */
    val title: String,
    /** Authors joined for display; empty when unknown. */
    val authors: String,
    val coverFile: File?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterCount: Int,
    /** Display title of every chapter, in reading order. */
    val chapterTitles: List<String>,
    /** Whether listening is on (the main button offers to pause), including while the voice prepares audio. */
    val isPlaying: Boolean,
    /** Whether audio is being prepared and nothing is heard yet. */
    val isBuffering: Boolean,
    /** Whether the end of the book was reached. */
    val isEnded: Boolean,
    /** Estimated position in the chapter. */
    val positionMs: Long,
    /** Estimated length of the chapter. */
    val durationMs: Long,
    val speed: Float,
    val problem: PlaybackProblem?,
) {
    /** Fraction of the chapter already heard, from 0 to 1. */
    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
