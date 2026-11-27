package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.tts.Voice
import java.io.File

/** A book opened for listening: what the facade player presents and renders. */
data class PlaybackBook(
    val id: String,
    /** Empty when unknown. */
    val title: String,
    val authors: List<String>,
    /** The cover image file, or null when the book has none. */
    val cover: File?,
    /** At least one chapter, in reading order. */
    val chapters: List<PlaybackChapter>,
    /** The voice that reads the book, or null when no usable voice is installed. */
    val voice: Voice?,
    /** Where listening resumes. */
    val position: ReadingPosition,
    /** Playback speed multiplier from the settings. */
    val speed: Float,
) {
    init {
        require(chapters.isNotEmpty()) { "A book needs at least one chapter" }
    }

    /** Length of each chapter's normalized text, in reading order. */
    val chapterLengths: List<Int> = chapters.map(PlaybackChapter::length)

    /** The end of the last chapter. */
    val end: ReadingPosition get() = ReadingPosition(chapters.lastIndex, chapters.last().length)
}

/** Chapter metadata; the text is loaded on demand. */
data class PlaybackChapter(
    /** Title from the book, or null when it has none. */
    val title: String?,
    /** Characters of normalized text. */
    val length: Int,
)
