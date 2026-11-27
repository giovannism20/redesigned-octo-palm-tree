package com.giovannimartins.rreader.ui.player

import android.content.ContentResolver
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.giovannimartins.rreader.playback.BookMediaItems
import com.giovannimartins.rreader.playback.PlaybackErrors
import java.io.File

/**
 * What [this] player (a controller of the playback service) has open, or null when no book is open.
 *
 * @param chapterTitles the titles from [chapterTitles], computed once per timeline change.
 */
internal fun Player.toNowPlaying(chapterTitles: List<String>): NowPlaying? {
    val bookId = currentMediaItem?.mediaId?.let(BookMediaItems::parse)?.bookId ?: return null
    val metadata = mediaMetadata
    val duration = contentDuration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
    return NowPlaying(
        bookId = bookId,
        title = metadata.title?.toString().orEmpty(),
        authors = metadata.artist?.toString().orEmpty(),
        coverFile =
            metadata.artworkUri
                ?.takeIf { it.scheme == ContentResolver.SCHEME_FILE }
                ?.path
                ?.let(::File),
        chapterIndex = currentMediaItemIndex,
        chapterTitle = metadata.subtitle?.toString().orEmpty(),
        chapterCount = currentTimeline.windowCount,
        chapterTitles = chapterTitles,
        isPlaying = playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        isEnded = playbackState == Player.STATE_ENDED,
        positionMs = contentPosition.coerceIn(0, duration),
        durationMs = duration,
        speed = playbackParameters.speed,
        problem = playerError?.let { problemOf(it.errorCode) },
    )
}

/** The display title of every chapter in [this] player's timeline. */
internal fun Player.chapterTitles(): List<String> {
    val timeline = currentTimeline
    val window = Timeline.Window()
    return List(timeline.windowCount) { index ->
        timeline
            .getWindow(index, window)
            .mediaItem.mediaMetadata.subtitle
            ?.toString()
            .orEmpty()
    }
}

private fun problemOf(errorCode: Int): PlaybackProblem =
    when (errorCode) {
        PlaybackErrors.NO_VOICE -> PlaybackProblem.NO_VOICE
        PlaybackErrors.BOOK_UNAVAILABLE -> PlaybackProblem.BOOK_UNAVAILABLE
        PlaybackErrors.SPEECH_FAILED -> PlaybackProblem.SPEECH_FAILED
        else -> PlaybackProblem.OTHER
    }
