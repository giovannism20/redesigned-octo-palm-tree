package com.giovannimartins.rreader.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi

/** A request to play a book, optionally from the start of a chapter. */
data class BookRequest(
    val bookId: String,
    val chapterIndex: Int?,
)

/** Names the chapters of a book that have no title of their own, for example "Chapter 3". */
fun interface ChapterLabeler {
    fun label(chapterIndex: Int): String
}

/**
 * The facade player's items: one per chapter, with media id `"bookId/chapterIndex"` (ADR 0003). Requests from
 * controllers are a book id, optionally with a chapter index.
 */
@OptIn(UnstableApi::class)
object BookMediaItems {
    private const val MICROS_PER_MILLI = 1_000L
    private val REQUEST =
        Regex("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:/([0-9]{1,6}))?")

    /** Media id of chapter [chapterIndex] of book [bookId]. */
    fun chapterId(
        bookId: String,
        chapterIndex: Int,
    ): String = "$bookId/$chapterIndex"

    /** Parses a media id sent by a controller; null when it is not a book id with an optional chapter. */
    fun parse(mediaId: String): BookRequest? =
        REQUEST.matchEntire(mediaId)?.let { match ->
            BookRequest(match.groupValues[1], match.groupValues[2].toIntOrNull())
        }

    /** A chapter's title, or the [labeler]'s name for it when the book has none. */
    fun chapterTitle(
        book: PlaybackBook,
        chapterIndex: Int,
        labeler: ChapterLabeler,
    ): String = book.chapters[chapterIndex].title?.takeIf(String::isNotBlank) ?: labeler.label(chapterIndex)

    /** The facade playlist of [book], with chapter durations measured by [clock]. */
    internal fun playlist(
        book: PlaybackBook,
        clock: ChapterClock,
        labeler: ChapterLabeler,
    ): List<MediaItemData> =
        book.chapters.mapIndexed { index, chapter ->
            val id = chapterId(book.id, index)
            val item =
                MediaItem
                    .Builder()
                    .setMediaId(id)
                    .setMediaMetadata(chapterMetadata(book, index, labeler))
                    .build()
            MediaItemData
                .Builder(id)
                .setMediaItem(item)
                .setDurationUs(clock.millisFor(chapter.length) * MICROS_PER_MILLI)
                .setIsSeekable(true)
                .build()
        }

    /** Metadata of the whole book, for the facade's playlist. */
    fun bookMetadata(book: PlaybackBook): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(book.title)
            .setArtist(book.authors.joinToString(", "))
            .setArtworkUri(book.cover?.let(Uri::fromFile))
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .build()

    /**
     * Metadata of one chapter. Notifications and the lock screen show the title and the artist, so those are
     * the book title and its authors; the chapter title is the subtitle.
     */
    private fun chapterMetadata(
        book: PlaybackBook,
        chapterIndex: Int,
        labeler: ChapterLabeler,
    ): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(book.title)
            .setAlbumTitle(book.title)
            .setArtist(book.authors.joinToString(", "))
            .setSubtitle(chapterTitle(book, chapterIndex, labeler))
            .setArtworkUri(book.cover?.let(Uri::fromFile))
            .setTrackNumber(chapterIndex + 1)
            .setTotalTrackCount(book.chapters.size)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
}
