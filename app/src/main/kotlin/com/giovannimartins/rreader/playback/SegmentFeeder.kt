package com.giovannimartins.rreader.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.SegmentAudio
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Keeps the internal [ExoPlayer] fed with rendered segments (ADR 0003): renders a window of upcoming segments
 * ahead of the playing one ([RenderAhead]), across chapter boundaries, and appends each to the internal
 * playlist as soon as it is ready. When the player runs out of audio it resumes it with the next segment.
 * Confined to the player's thread; rendering itself runs on the renderer's dispatchers.
 */
internal class SegmentFeeder(
    private val exo: ExoPlayer,
    private val renderer: SegmentRenderer,
    private val plans: ChapterPlans,
    private val speech: SpeechNormalizer,
    private val scope: CoroutineScope,
    private val events: Events,
) {
    /** Called on the player's thread. */
    interface Events {
        /** The start position was resolved to the segment that holds it, or to the end of the book (null). */
        fun onStartResolved(start: SegmentCursor?)

        /** A segment was queued, or every remaining segment of the book has been queued. */
        fun onQueueChanged()

        /** A chapter's text could not be read; the feeder stopped. */
        fun onFailure(error: PlaybackException)

        /**
         * [voice] could not render a segment, for [reason]; the feeder stopped. An engine that could not start
         * ([SynthesisFailure.ENGINE_UNAVAILABLE]) was tried twice in a row first.
         */
        fun onSpeechFailed(
            voice: Voice,
            reason: SynthesisFailure,
        )
    }

    private val queue = SegmentQueue()
    private var source: Source? = null
    private var pendingStart: ReadingPosition? = null
    private var pendingRewind = 0
    private var last: SegmentCursor? = null
    private var job: Job? = null

    /** Whether segments are being rendered right now. */
    val isRendering: Boolean get() = job?.isActive == true

    /** Whether every remaining segment of the book is queued. */
    var isExhausted: Boolean = false
        private set

    /** The last queued segment, or null when nothing is queued. */
    val lastQueued: QueuedSegment? get() = queue.last()

    /**
     * Clears the internal playlist and renders the book from the segment that holds [from], or, when
     * [rewindChars] is positive, from a little before it (see [ResumeRewind.target]).
     */
    fun restart(
        bookId: String,
        voice: Voice,
        from: ReadingPosition,
        rewindChars: Int = 0,
    ) {
        clear()
        source = Source(bookId, voice)
        pendingStart = from
        pendingRewind = rewindChars
        pump()
    }

    /** Stops rendering and empties the internal playlist. */
    fun clear() {
        job?.cancel()
        job = null
        source = null
        pendingStart = null
        last = null
        isExhausted = false
        queue.clear()
        if (exo.mediaItemCount > 0) exo.clearMediaItems()
    }

    /** The queued segment the internal player is on (possibly finished), or null while nothing is queued. */
    fun current(): QueuedSegment? = queue.find(exo.currentMediaItem?.mediaId)

    /** How far the internal player is into [segment], from 0.0 to 1.0. */
    fun progressIn(segment: QueuedSegment): Double =
        when {
            exo.currentMediaItem?.mediaId != segment.key -> 1.0
            exo.playbackState == Player.STATE_ENDED -> 1.0
            else -> (exo.currentPosition.toDouble() / segment.durationMillis).coerceIn(0.0, 1.0)
        }

    /** Audio left at 1.0× in the chapter being played; null while that is unknown (see [SegmentQueue]). */
    val audioLeftInChapter: Long?
        get() =
            current()?.let { segment ->
                plans.chapterLength(segment.chapterIndex)?.let { length ->
                    queue.audioLeftInChapter(segment.key, exo.currentPosition, length, isExhausted)
                }
            }

    /** Whether the internal player is on the last segment of its chapter. */
    val isOnChapterEnd: Boolean
        get() =
            current()?.let { segment -> plans.chapterLength(segment.chapterIndex)?.let { segment.spanEnd >= it } } ==
                true

    /** The internal player moved to another segment: drops played items and renders further ahead. */
    fun onAdvanced() {
        val played = exo.currentMediaItemIndex
        if (played >= DROP_PLAYED_BATCH) {
            queue.dropFirst(played)
            exo.removeMediaItems(0, played)
        }
        pump()
    }

    private fun pump() {
        val source = source
        if (source != null && !isExhausted && job?.isActive != true) {
            // Listening waits for these renders, so background renders hold back until the window is full.
            job = scope.launch { renderer.inForeground { renderAhead(source) } }
        }
    }

    private suspend fun renderAhead(source: Source) {
        try {
            var rendering = true
            while (rendering && wantsMore()) {
                val cursor = nextCursor()
                if (cursor == null) {
                    isExhausted = true
                    events.onQueueChanged()
                } else {
                    rendering = render(source, cursor)
                }
            }
        } catch (e: IOException) {
            Diagnostics.recordHandled("read chapter text", e)
            events.onFailure(PlaybackErrors.bookUnavailable(e))
        }
    }

    private fun wantsMore(): Boolean =
        !isExhausted && RenderAhead.wantsMore(queue.lookahead(exo.currentMediaItem?.mediaId))

    /** The resolved start after a restart, then the segment after the last one rendered. */
    private suspend fun nextCursor(): SegmentCursor? {
        val start = pendingStart
        return if (start != null) {
            pendingStart = null
            plans.locate(start, pendingRewind).also(events::onStartResolved)
        } else {
            last?.let { plans.after(it) }
        }
    }

    /** Renders and queues [cursor]; false when rendering failed and the feeder stopped. */
    private suspend fun render(
        source: Source,
        cursor: SegmentCursor,
    ): Boolean {
        last = cursor
        // For example a "* * *" scene break: nothing to say.
        val request = cursor.request(source.bookId, source.voice, speech) ?: return true
        var audio = renderer.render(request)
        // An engine that could not start released what it had loaded (a model, memory): a second try may work.
        if (audio is SegmentAudio.Failed && audio.reason == SynthesisFailure.ENGINE_UNAVAILABLE) {
            audio = renderer.render(request)
        }
        return when (audio) {
            is SegmentAudio.Ready -> {
                append(cursor, audio)
                true
            }

            is SegmentAudio.Failed -> {
                events.onSpeechFailed(source.voice, audio.reason)
                false
            }
        }
    }

    private fun append(
        cursor: SegmentCursor,
        audio: SegmentAudio.Ready,
    ) {
        val segment =
            QueuedSegment(
                chapterIndex = cursor.plan.index,
                range = cursor.range,
                spanEnd = cursor.plan.spanEnd(cursor.index),
                file = audio.file,
                durationMillis = audio.durationMillis,
            )
        val item =
            MediaItem
                .Builder()
                .setMediaId(segment.key)
                .setUri(Uri.fromFile(audio.file))
                .build()
        val first = queue.isEmpty()
        val starved = !first && exo.playbackState == Player.STATE_ENDED
        // Queue first: the internal player reports the new item synchronously from the calls below.
        queue.add(segment)
        if (first) exo.setMediaItems(listOf(item), true) else exo.addMediaItem(item)
        if (starved) exo.seekToDefaultPosition(exo.mediaItemCount - 1)
        events.onQueueChanged()
    }

    private data class Source(
        val bookId: String,
        val voice: Voice,
    )

    private companion object {
        /** Played items are dropped in batches, keeping the playlist short without changing it on every item. */
        const val DROP_PLAYED_BATCH = 8
    }
}
