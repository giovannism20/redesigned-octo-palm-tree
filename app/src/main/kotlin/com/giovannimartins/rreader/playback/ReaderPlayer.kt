package com.giovannimartins.rreader.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.di.ApplicationScope
import com.giovannimartins.rreader.di.DefaultDispatcher
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.Voice
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/** Collaborators of [ReaderPlayer]. */
data class ReaderPlayerDependencies
    @Inject
    constructor(
        val content: PlaybackContent,
        val renderer: SegmentRenderer,
        val planner: SegmentPlanner,
        val speech: SpeechNormalizer,
        val rates: SpeakingRateTracker,
        /** Outlives the player, so the last position and speed are saved even after a release. */
        @ApplicationScope val saveScope: CoroutineScope,
        @DefaultDispatcher val cpu: CoroutineDispatcher,
    )

/**
 * The facade player of ADR 0003. To the media session it is a playlist with one item per chapter of a book,
 * with a continuous position and duration in each chapter derived from characters and the voice's speaking
 * rate. Underneath, an internal [ExoPlayer] plays rendered segments that a [SegmentFeeder] keeps queued, and
 * every command is mapped onto reading positions (ADR 0006): seeks land at the start of the segment that holds
 * their target, ±15 s cross chapter boundaries, and headset next/previous are ±15 s rather than chapter jumps.
 * When the internal player runs out of rendered audio the facade reports buffering until the next segment is
 * ready; the end of the book is [Player.STATE_ENDED].
 *
 * Controllers open a book with `setMediaItem(MediaItem.Builder().setMediaId(bookId).build())`, then `prepare()`
 * and `play()`; it resumes at the start of the segment that holds the saved position. The position is saved
 * on every segment change, pause, stop and seek (throttled).
 */
@OptIn(UnstableApi::class)
class ReaderPlayer internal constructor(
    looper: Looper,
    private val exo: ExoPlayer,
    private val deps: ReaderPlayerDependencies,
    private val labeler: ChapterLabeler,
    elapsedMillis: () -> Long,
) : SimpleBasePlayer(looper) {
    private val handler = Handler(looper)
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                handler.asCoroutineDispatcher() +
                CoroutineExceptionHandler { _, e ->
                    Diagnostics.recordHandled("playback", e)
                    fail(PlaybackException("Playback failed", e, PlaybackException.ERROR_CODE_UNSPECIFIED))
                },
        )
    private val plans = ChapterPlans(deps.content, deps.planner, deps.cpu)
    private val feeder = SegmentFeeder(exo, deps.renderer, plans, deps.speech, scope, FeederEvents())
    private val position = PlaybackPosition(feeder)
    private val loader = BookLoader(scope, deps.content) { handler.post(it) }
    private val saver = PositionSaver(deps.saveScope, elapsedMillis, deps.content::savePosition)

    private var book: PlaybackBook? = null
    private var playlist: List<MediaItemData> = emptyList()
    private var bookMetadata: MediaMetadata = MediaMetadata.EMPTY
    private var prepared = false
    private var playWhenReady = false
    private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var error: PlaybackException? = null
    private var ended = false
    private var speed = AppSettings.DEFAULT_SPEED
    private var reportedChapter = 0
    private var discontinuity: Int? = null

    init {
        exo.addListener(InternalPlayerListener())
    }

    override fun getState(): State {
        val book = book
        val playbackState = playbackState()
        val builder =
            State
                .Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, playWhenReadyReason)
                .setPlaybackState(playbackState)
                .setPlaybackSuppressionReason(exo.playbackSuppressionReason)
                .setPlayerError(error)
                .setIsLoading(
                    feeder.isRendering &&
                        (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY),
                ).setPlaybackParameters(PlaybackParameters(speed))
                .setAudioAttributes(exo.audioAttributes)
                .setSeekBackIncrementMs(SEEK_INCREMENT.inWholeMilliseconds)
                .setSeekForwardIncrementMs(SEEK_INCREMENT.inWholeMilliseconds)
        if (book != null) {
            val chapter = position.chapter()
            reportedChapter = chapter
            builder
                .setPlaylist(playlist)
                .setPlaylistMetadata(bookMetadata)
                .setCurrentMediaItemIndex(chapter)
                .setContentPositionMs(position.supplier())
                .setContentBufferedPositionMs(
                    PositionSupplier.getConstant(position.bufferedMillis(chapter, book.chapters[chapter].length)),
                )
            discontinuity?.let { reason -> builder.setPositionDiscontinuity(reason, position.millis()) }
        }
        discontinuity = null
        return builder.build()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val request = mediaItems.singleOrNull()?.mediaId?.let(BookMediaItems::parse)
        return when {
            request == null -> {
                closeBook()
                loader.forget()
                if (mediaItems.isNotEmpty()) error = PlaybackErrors.bookUnavailable()
                Futures.immediateVoidFuture()
            }

            request.bookId == book?.id && request.chapterIndex == null -> {
                Futures.immediateVoidFuture()
            }

            else -> {
                openBook(request, resumeAt = null)
            }
        }
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (prepared && error == null) return Futures.immediateVoidFuture()
        // After a failure, or without a voice, load the book again: a voice may have been installed since.
        val retry = loader.request?.takeIf { !loader.isLoading && (error != null || book?.voice == null) }
        val resumeAt = book?.let { position.current() }
        prepared = true
        error = null
        return if (retry != null) {
            openBook(retry, resumeAt)
        } else {
            start()
            Futures.immediateVoidFuture()
        }
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        exo.playWhenReady = playWhenReady
        if (!playWhenReady) savePosition(force = true)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        position.anchor = position.current()
        savePosition(force = true)
        position.freeze()
        feeder.clear()
        exo.stop()
        prepared = false
        error = null
        ended = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        savePosition(force = true)
        saver.close()
        loader.cancel()
        scope.cancel()
        exo.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val book = book
        val lengths = book?.chapterLengths.orEmpty()
        val target =
            when (seekCommand) {
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_TO_PREVIOUS -> {
                    book?.let { SeekTargets.offset(position.current(), -SEEK_INCREMENT, lengths, position.clock, null) }
                }

                Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_TO_NEXT -> {
                    book?.let {
                        val spanEnd = position.spanEnd(plans)
                        SeekTargets.offset(position.current(), SEEK_INCREMENT, lengths, position.clock, spanEnd)
                    }
                }

                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                    SeekTargets.inChapter(position.chapter(), positionMs, lengths, position.clock)
                }

                Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> {
                    val chapterStart = if (positionMs == C.TIME_UNSET) 0 else positionMs
                    SeekTargets.inChapter(mediaItemIndex, chapterStart, lengths, position.clock)
                }

                else -> {
                    null
                }
            }
        target?.let(::seekTo)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        val newSpeed = playbackParameters.speed.coerceIn(AppSettings.SPEED_RANGE)
        speed = newSpeed
        // A pitch of 1.0 keeps the voice natural at any speed.
        exo.playbackParameters = PlaybackParameters(newSpeed)
        deps.saveScope.launch { deps.content.saveSpeed(newSpeed) }
        return Futures.immediateVoidFuture()
    }

    private fun playbackState(): Int =
        when {
            error != null || !prepared -> Player.STATE_IDLE
            book == null || ended -> Player.STATE_ENDED
            feeder.current() != null && exo.playbackState == Player.STATE_READY -> Player.STATE_READY
            else -> Player.STATE_BUFFERING
        }

    private fun openBook(
        request: BookRequest,
        resumeAt: ReadingPosition?,
    ): ListenableFuture<*> {
        closeBook()
        return loader.load(request) { loaded -> onBookLoaded(loaded, request, resumeAt) }
    }

    /** Saves where the open book was left and forgets it. */
    private fun closeBook() {
        savePosition(force = true)
        position.freeze()
        feeder.clear()
        plans.reset(null)
        book = null
        playlist = emptyList()
        bookMetadata = MediaMetadata.EMPTY
        error = null
        ended = false
        position.anchor = ReadingPosition.START
        loader.cancel()
    }

    private fun onBookLoaded(
        loaded: PlaybackBook?,
        request: BookRequest,
        resumeAt: ReadingPosition?,
    ) {
        if (loaded == null) {
            error = PlaybackErrors.bookUnavailable()
            return
        }
        book = loaded
        plans.reset(loaded)
        speed = loaded.speed
        exo.playbackParameters = PlaybackParameters(speed)
        position.clock = ChapterClock(deps.rates.rateFor(loaded.voice?.id))
        playlist = BookMediaItems.playlist(loaded, position.clock, labeler)
        bookMetadata = BookMediaItems.bookMetadata(loaded)
        val requestedChapter = request.chapterIndex?.takeIf { it in loaded.chapters.indices }
        position.anchor = resumeAt ?: requestedChapter?.let { ReadingPosition(it, 0) } ?: loaded.position
        reportedChapter = position.anchor.chapterIndex
        start()
    }

    /** Renders from the anchor when the player is prepared and the open book has a voice. */
    private fun start() {
        val book = book?.takeIf { prepared } ?: return
        val voice = book.voice
        if (voice == null) {
            error = PlaybackErrors.noVoice()
        } else {
            position.freeze()
            ended = false
            refreshClock(book, voice)
            // Preparing before playback starts lets the internal player request audio focus right away,
            // while the listener is still in the app (ADR 0014).
            if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
            feeder.restart(book.id, voice, position.anchor)
        }
    }

    /** Adopts the voice's latest speaking rate for chapter durations if it moved noticeably. */
    private fun refreshClock(
        book: PlaybackBook,
        voice: Voice,
    ) {
        val rate = deps.rates.rateFor(voice.id)
        val current = position.clock.rate
        if (abs(rate.charsPerSecond - current.charsPerSecond) > current.charsPerSecond * CLOCK_TOLERANCE) {
            position.clock = ChapterClock(rate)
            playlist = BookMediaItems.playlist(book, position.clock, labeler)
        }
    }

    private fun seekTo(target: ReadingPosition) {
        position.freeze()
        position.anchor = target
        discontinuity = Player.DISCONTINUITY_REASON_SEEK
        book?.let { saver.save(it.id, target, force = false) }
        if (error == null) start()
    }

    /** Ends the book once every segment was queued and played. */
    private fun finishBookIfDrained() {
        val book = book?.takeIf { !ended && feeder.isExhausted }
        val drained = feeder.lastQueued == null || exo.playbackState == Player.STATE_ENDED
        if (book != null && drained) {
            ended = true
            position.anchor = book.end
            saver.save(book.id, book.end, force = true)
        }
    }

    private fun fail(failure: PlaybackException) {
        position.anchor = position.current()
        savePosition(force = true)
        position.freeze()
        feeder.clear()
        error = failure
        invalidateState()
    }

    private fun savePosition(force: Boolean) {
        book?.let { saver.save(it.id, position.current(), force) }
    }

    /** Mirrors the internal player into the facade's state. */
    private inner class InternalPlayerListener : Player.Listener {
        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            val segment = feeder.current()
            val book = book
            if (segment != null && book != null) {
                if (segment.chapterIndex != reportedChapter && discontinuity == null) {
                    // The listener reached the next chapter: a natural transition, not a seek.
                    discontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    book.voice?.let { refreshClock(book, it) }
                }
                position.anchor = ReadingPosition(segment.chapterIndex, segment.range.start)
                saver.save(book.id, position.anchor, force = true)
                feeder.onAdvanced()
                invalidateState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            finishBookIfDrained()
            invalidateState()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            // Changes the facade did not ask for: audio focus loss (a phone call), headphones unplugged.
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                this@ReaderPlayer.playWhenReady = playWhenReady
                playWhenReadyReason = reason
                if (!playWhenReady) savePosition(force = true)
            }
            invalidateState()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = invalidateState()

        override fun onIsPlayingChanged(isPlaying: Boolean) = invalidateState()

        override fun onPlayerError(error: PlaybackException) {
            Diagnostics.recordHandled("internal player", error)
            // The segment's audio may be damaged: render it again when playback is retried.
            feeder.current()?.let { segment -> scope.launch { deps.renderer.discard(segment.file) } }
            fail(error)
        }
    }

    private inner class FeederEvents : SegmentFeeder.Events {
        override fun onStartResolved(start: ReadingPosition?) {
            if (start != null) {
                position.anchor = start
                invalidateState()
            }
        }

        override fun onQueueChanged() {
            finishBookIfDrained()
            invalidateState()
        }

        override fun onFailure(error: PlaybackException) = fail(error)
    }

    private companion object {
        /** Seek back/forward step, also used for headset previous/next. */
        val SEEK_INCREMENT = 15.seconds

        /** Relative change of the speaking rate below which chapter durations are kept as they are. */
        const val CLOCK_TOLERANCE = 0.02

        val COMMANDS: Player.Commands =
            Player.Commands
                .Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_RELEASE,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                ).build()
    }
}
