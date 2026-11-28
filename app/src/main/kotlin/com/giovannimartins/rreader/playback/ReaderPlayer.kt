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
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.di.ApplicationScope
import com.giovannimartins.rreader.di.DefaultDispatcher
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.model.TextRange
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.RenderSpeedTracker
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.VoiceIds
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration
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
        /** Tells whether a voice keeps up with listening on this phone. */
        val renderSpeeds: RenderSpeedTracker,
        /** Where the segment being spoken is published for the UI. */
        val status: ListeningStatus,
        /** Renders slow voices ahead while the phone charges and the book is not being listened to. */
        val prerender: PrerenderJobs,
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
 * on every segment change, pause, stop and seek (throttled). The segment being spoken and the [SleepTimer] are
 * published in [ListeningStatus], and [handle] runs the app's custom commands ([ReaderCommands]), among them a voice
 * change, which takes effect at the segment being spoken without reopening the book. The listening session (book,
 * voice, listening or not) is published there too; a slow voice's book is rendered ahead only while it is not being
 * listened to.
 */
@OptIn(UnstableApi::class)
class ReaderPlayer internal constructor(
    looper: Looper,
    private val exo: ExoPlayer,
    private val deps: ReaderPlayerDependencies,
    private val labeler: ChapterLabeler,
    /** Measures elapsed time and schedules the sleep timer; a fake clock in tests. */
    private val clock: Clock,
    sleepFade: Duration = SleepTimer.DEFAULT_FADE,
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
    private val saver = PositionSaver(deps.saveScope, clock::elapsedRealtime, deps.content::savePosition)
    private val sleepHost = SleepTimerHost()
    private val sleepTimer = SleepTimer(clock, looper, sleepHost, sleepFade)
    private val pauses = PauseClock(clock)
    private val voiceSwitch = VoiceSwitch()
    private val sessionHost = SessionHost()

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

    /** Whether the next start rewinds a little because listening resumes after a long pause. */
    private var rewindOnStart = false

    /** The voice whose audio ran out while the listener was listening to the open book; null when it did not. */
    private var stalledVoice: Voice? = null

    /** The lookup of the voice asked for by the last [ReaderCommand.RefreshVoice]; a newer one cancels it. */
    private var voiceRefresh: Job? = null

    init {
        exo.addListener(InternalPlayerListener())
        addListener(sleepHost)
        addListener(sessionHost)
    }

    override fun getState(): State {
        val book = book
        val playbackState =
            when {
                error != null || !prepared -> Player.STATE_IDLE
                book == null || ended -> Player.STATE_ENDED
                feeder.current() != null && exo.playbackState == Player.STATE_READY -> Player.STATE_READY
                else -> Player.STATE_BUFFERING
            }
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
        if (!playWhenReady) {
            pauses.stopped()
        } else if (pauses.resumesAfterLongPause()) {
            rewindFromHere()
        }
        this.playWhenReady = playWhenReady
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        exo.playWhenReady = playWhenReady
        if (!playWhenReady) savePosition(force = true)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        position.anchor = position.current()
        pauses.stopped()
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
        deps.status.publishSegment(null)
        deps.status.publishSession(null)
        sleepTimer.release()
        deps.status.publishSleepTimer(SleepTimerState.Off)
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
        feeder.speed = newSpeed
        exo.playbackParameters = SegmentSpeed.parameters(speed, feeder.current(), book?.voice)
        // A slower speed may let the voice keep up again.
        sessionHost.update()
        deps.saveScope.launch { deps.content.saveSpeed(newSpeed) }
        return Futures.immediateVoidFuture()
    }

    /**
     * Runs a custom [command] from the app; false when it does not apply now.
     *
     * [ReaderCommand.ReadFrom] plays the open book from the start of the segment that holds its position (the
     * reader's "read from here"), preparing the player again when it was stopped or failed; it does not apply
     * when no book is open or the position is not in it. An offset past the end of its chapter means the end
     * of that chapter. [ReaderCommand.SetSleepTimer] needs an open book; the timer then keeps running when
     * another book is opened, and turns off at the end of a book.
     */
    fun handle(command: ReaderCommand): Boolean =
        when (command) {
            is ReaderCommand.ReadFrom -> {
                val target = command.position
                val length = book?.chapterLengths?.getOrNull(target.chapterIndex)
                if (length != null) {
                    seekTo(ReadingPosition(target.chapterIndex, target.charOffset.coerceAtMost(length)))
                    invalidateState()
                    if (playbackState == Player.STATE_IDLE) prepare()
                    play()
                }
                length != null
            }

            is ReaderCommand.SetSleepTimer -> {
                val open = book != null
                if (open) sleepTimer.start(command.setting)
                open
            }

            ReaderCommand.CancelSleepTimer -> {
                sleepTimer.cancel()
                true
            }

            ReaderCommand.RefreshVoice -> {
                val open = book
                if (open != null) {
                    // Two choices in a row: the lookup of the first could end last, so the last choice cancels it.
                    voiceRefresh?.cancel()
                    voiceRefresh = scope.launch { voiceSwitch.apply(open.id, deps.content.voiceFor(open.id)) }
                }
                open != null
            }
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
        pauses.forget()
        rewindOnStart = false
        deps.status.publishSegment(null)
        loader.cancel()
        voiceSwitch.replaced = null
        sessionHost.update()
    }

    private fun onBookLoaded(
        found: PlaybackBook?,
        request: BookRequest,
        resumeAt: ReadingPosition?,
    ) {
        if (found == null) {
            error = PlaybackErrors.bookUnavailable()
            return
        }
        val loaded = voiceSwitch.adopt(found)
        stalledVoice = null
        book = loaded
        plans.reset(loaded)
        speed = loaded.speed
        exo.playbackParameters = SegmentSpeed.parameters(speed, feeder.current(), book?.voice)
        position.clock = ChapterClock(deps.rates.rateFor(loaded.voice?.id))
        playlist = BookMediaItems.playlist(loaded, position.clock, labeler)
        bookMetadata = BookMediaItems.bookMetadata(loaded)
        val requestedChapter = request.chapterIndex?.takeIf { it in loaded.chapters.indices }
        position.anchor = resumeAt ?: requestedChapter?.let { ReadingPosition(it, 0) } ?: loaded.position
        reportedChapter = position.anchor.chapterIndex
        // Back to a book left a while ago: resume a little earlier, as after a long pause.
        if (resumeAt == null && requestedChapter == null && loaded.pausedFor?.let(ResumeRewind::isLong) == true) {
            rewindOnStart = true
        }
        start()
        sessionHost.update()
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
            val rewindChars = if (rewindOnStart) position.clock.charsFor(MAX_REWIND_MS) else 0
            rewindOnStart = false
            feeder.restart(book.id, voice, position.anchor, rewindChars, speed)
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
        pauses.forget()
        rewindOnStart = false
        discontinuity = Player.DISCONTINUITY_REASON_SEEK
        book?.let { saver.save(it.id, target, force = false) }
        if (error == null) start()
    }

    /**
     * Restarts a little before where listening stopped ([ResumeRewind]); a player that is not playing the
     * book yet rewinds when it starts.
     */
    private fun rewindFromHere() {
        rewindOnStart = true
        if (book != null && prepared && error == null) {
            position.anchor = position.current()
            position.freeze()
            discontinuity = Player.DISCONTINUITY_REASON_SEEK
            start()
        }
    }

    /** Ends the book once every segment was queued and played. */
    private fun finishBookIfDrained() {
        val book = book?.takeIf { !ended && feeder.isExhausted }
        val drained = feeder.lastQueued == null || exo.playbackState == Player.STATE_ENDED
        if (book != null && drained) {
            ended = true
            position.anchor = book.end
            saver.save(book.id, book.end, force = true)
            sleepTimer.cancel()
        }
    }

    private fun fail(failure: PlaybackException) {
        position.anchor = position.current()
        pauses.stopped()
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
            exo.playbackParameters = SegmentSpeed.parameters(speed, feeder.current(), book?.voice)
            val segment = feeder.current()
            val book = book
            if (segment != null && book != null) {
                // The listener reached the next chapter: a natural transition, not a seek.
                val reachedNextChapter = segment.chapterIndex != reportedChapter && discontinuity == null
                if (reachedNextChapter) {
                    discontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    book.voice?.let { refreshClock(book, it) }
                }
                position.anchor = ReadingPosition(segment.chapterIndex, segment.range.start)
                deps.status.publish(book, segment.chapterIndex, segment.range)
                saver.save(book.id, position.anchor, force = true)
                feeder.onAdvanced()
                invalidateState()
                if (reachedNextChapter) sleepTimer.onChapterEnded()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            finishBookIfDrained()
            noteStall(playbackState)
            // Out of rendered audio right at the end of a chapter: the chapter ended before the next one is ready.
            if (playbackState == Player.STATE_ENDED && !ended && feeder.isOnChapterEnd) sleepTimer.onChapterEnded()
            invalidateState()
        }

        /**
         * The internal player ran out of audio while the listener listens and the book goes on: the voice did not
         * render in time. Remembered so the session can say why when the voice is measured to be too slow here.
         */
        private fun noteStall(playbackState: Int) {
            val voice = book?.voice ?: return
            val ranOut = playbackState == Player.STATE_ENDED && !feeder.isExhausted
            if (ranOut && playWhenReady && !ended) {
                stalledVoice = voice
                sessionHost.update()
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            // Changes the facade did not ask for: audio focus loss (a phone call), headphones unplugged.
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                this@ReaderPlayer.playWhenReady = playWhenReady
                playWhenReadyReason = reason
                if (!playWhenReady) {
                    pauses.stopped()
                    savePosition(force = true)
                }
            }
            invalidateState()
        }

        /** A phone call interrupts listening; when it ends after a long while, listening rewinds a little. */
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                pauses.stopped()
            } else if (playWhenReady && pauses.resumesAfterLongPause()) {
                rewindFromHere()
            }
            invalidateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = invalidateState()

        override fun onPlayerError(error: PlaybackException) {
            Diagnostics.recordHandled("internal player", error)
            // The segment's audio may be damaged: render it again when playback is retried.
            feeder.current()?.let { segment -> scope.launch { deps.renderer.discard(segment.file) } }
            fail(error)
        }
    }

    /**
     * Changes the voice of the open book while it is listened to (see [ReaderCommand.RefreshVoice]), and replaces, for
     * the rest of the session, a neural voice that could not start with the phone's voice.
     */
    private inner class VoiceSwitch {
        /** Neural voices that could not start in this session, by id, and the phone's voice that reads instead. */
        private val replacements = mutableMapOf<String, Voice>()

        /** The voice chosen for the open book that the phone's voice replaces now; null when none is replaced. */
        var replaced: Voice? = null

        /** [found], just loaded, read by the voice that can read it in this session; notes a replaced voice. */
        fun adopt(found: PlaybackBook): PlaybackBook {
            val reading = reading(found.voice)
            replaced = found.voice?.takeIf { it.id != reading?.id }
            return found.copy(voice = reading)
        }

        /**
         * Book [bookId], when it is still the open one, is now read by [voice] (or the phone's voice, when [voice]
         * could not start in this session): when that is another voice, listening goes on with it from the start of
         * the segment being spoken, keeping the position, also when the player is paused (it renders ahead with the
         * new voice). A failure the voice caused (it was deleted, or failed to speak) is cleared, so listening
         * continues; without any voice, the player asks for one.
         */
        fun apply(
            bookId: String,
            voice: Voice?,
        ) {
            val open = book?.takeIf { it.id == bookId } ?: return
            val reading = reading(voice)
            replaced = voice?.takeIf { it.id != reading?.id }
            val voiceFailed = error?.errorCode in VOICE_ERRORS
            if (reading?.id != open.voice?.id || voiceFailed) {
                switchTo(open.copy(voice = reading), voiceFailed)
            } else {
                sessionHost.update()
            }
        }

        /**
         * [voice], a neural voice, could not start twice in a row: the phone's voice reads instead, for the rest of
         * the session, from the start of the segment being spoken, and the listening session says which voice it
         * replaces. Without a phone voice, the player fails for [reason] as for any voice.
         */
        fun fallBackFrom(
            voice: Voice,
            reason: SynthesisFailure,
        ) {
            scope.launch {
                val phone = deps.content.phoneVoice()
                val open = book?.takeIf { it.voice?.id == voice.id } ?: return@launch
                if (phone == null) {
                    fail(PlaybackErrors.speechFailed(reason))
                } else {
                    replacements[voice.id] = phone
                    replaced = voice
                    switchTo(open.copy(voice = phone), voiceFailed = true)
                }
            }
        }

        private fun reading(chosen: Voice?): Voice? = chosen?.let { replacements[it.id] ?: it }

        private fun switchTo(
            changed: PlaybackBook,
            voiceFailed: Boolean,
        ) {
            book = changed
            if (changed.voice == null) {
                fail(PlaybackErrors.noVoice())
            } else {
                if (voiceFailed) error = null
                if (error == null && prepared) {
                    position.anchor = position.current()
                    discontinuity = Player.DISCONTINUITY_REASON_INTERNAL
                    start()
                }
                invalidateState()
            }
            sessionHost.update()
        }
    }

    /**
     * Publishes the [ListeningSession] when it changes, and while the open book is not being listened to (paused,
     * stopped, just opened) asks for it to be rendered ahead if its voice is slower than real time (ADR 0018). The
     * pre-render job stops by itself as soon as listening starts ([Prerenderer]).
     */
    private inner class SessionHost : Player.Listener {
        private var published: ListeningSession? = null

        /** The book and slow voice asked to be rendered ahead, until rendering ahead is not wanted any more. */
        private var askedAhead: Pair<String, String>? = null

        // The single callbacks run as the state changes; onEvents would come later, in a message of its own.
        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) = update()

        override fun onPlaybackStateChanged(playbackState: Int) = update()

        override fun onPlayerErrorChanged(error: PlaybackException?) = update()

        fun update() {
            val open = book
            val listening = open != null && prepared && playWhenReady && error == null && !ended
            val slow =
                stalledVoice?.takeIf { it == open?.voice && deps.renderSpeeds.keepsUp(it.id, speed) == false }
            val session = open?.let { ListeningSession(it.id, it.voice, listening, voiceSwitch.replaced, slow) }
            if (session != published) {
                published = session
                deps.status.publishSession(session)
            }
            val slowVoice = open?.voice?.takeIf { it.slowToRender && !listening && !ended && error == null }
            val ahead = slowVoice?.let { open.id to it.id }
            if (ahead != askedAhead) {
                askedAhead = ahead
                ahead?.let { (bookId, voiceId) -> deps.prerender.schedule(bookId, voiceId) }
            }
        }
    }

    /** Gives the sleep timer what it needs from the player, and tells it when audio starts or stops. */
    private inner class SleepTimerHost :
        SleepTimer.Host,
        Player.Listener {
        override val isPlaying: Boolean get() = this@ReaderPlayer.isPlaying

        override fun chapterAudioLeftMillis(): Long? = feeder.audioLeftInChapter?.let { (it / speed).roundToLong() }

        override fun setVolume(volume: Float) {
            exo.volume = volume
        }

        override fun sleep() = pause()

        override fun onStateChanged(state: SleepTimerState) = deps.status.publishSleepTimer(state)

        override fun onIsPlayingChanged(isPlaying: Boolean) = sleepTimer.onPlayingChanged()
    }

    private inner class FeederEvents : SegmentFeeder.Events {
        override fun onStartResolved(start: SegmentCursor?) {
            if (start == null) {
                deps.status.publishSegment(null)
            } else {
                position.anchor = start.position
                deps.status.publish(book, start.plan.index, start.range)
                invalidateState()
            }
        }

        override fun onQueueChanged() {
            exo.playbackParameters = SegmentSpeed.parameters(speed, feeder.current(), book?.voice)
            finishBookIfDrained()
            invalidateState()
        }

        override fun onFailure(error: PlaybackException) = fail(error)

        override fun onSpeechFailed(
            voice: Voice,
            reason: SynthesisFailure,
        ) {
            // A neural voice that cannot start gives way to the phone's voice instead of stopping the book.
            if (reason == SynthesisFailure.ENGINE_UNAVAILABLE && VoiceIds.neuralKey(voice.id) != null) {
                voiceSwitch.fallBackFrom(voice, reason)
            } else {
                fail(PlaybackErrors.speechFailed(reason))
            }
        }
    }

    private companion object {
        /** Seek back/forward step, also used for headset previous/next. */
        val SEEK_INCREMENT = 15.seconds

        /** Relative change of the speaking rate below which chapter durations are kept as they are. */
        const val CLOCK_TOLERANCE = 0.02

        val MAX_REWIND_MS = ResumeRewind.MAX_REWIND.inWholeMilliseconds

        /** Failures a voice causes, which another voice can end. */
        val VOICE_ERRORS = setOf(PlaybackErrors.NO_VOICE, PlaybackErrors.SPEECH_FAILED)

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

/** Publishes [range] of chapter [chapterIndex] of [book] as the segment being spoken, when a book is open. */
private fun ListeningStatus.publish(
    book: PlaybackBook?,
    chapterIndex: Int,
    range: TextRange,
) {
    book?.let { publishSegment(SpokenSegment(it.id, chapterIndex, range)) }
}
