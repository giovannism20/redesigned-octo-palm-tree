package com.giovannimartins.rreader.playback

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the facade player through a real internal ExoPlayer on a fake clock. Sentences are ~38 characters,
 * below the speaking-rate tracker's minimum sample, so the chapter clock stays at the default rate and
 * expected positions can be computed exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderPlayerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val texts = listOf(chapterText(0, 12), chapterText(1, 12))
    private val planner = SegmentPlanner()
    private val clock = ChapterClock(SpeakingRate.DEFAULT)
    private val step = ReadingEstimates.charactersFor(15.seconds, SpeakingRate.DEFAULT, 1f)
    private val engine = FakeSpeechEngine()
    private val content = FakePlaybackContent(book(texts), texts)
    private lateinit var internal: ExoPlayer
    private lateinit var player: ReaderPlayer

    @Before
    fun setUp() {
        val io = UnconfinedTestDispatcher()
        val speech = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()
        internal =
            ExoPlayer
                .Builder(context)
                .setClock(FakeClock(true))
                .setAudioAttributes(speech, true)
                .build()
        val rates = SpeakingRateTracker()
        val renderer = SegmentRenderer(engine, AudioCache(folder.root), rates, Clock.systemUTC(), io)
        val dependencies =
            ReaderPlayerDependencies(content, renderer, planner, SpeechNormalizer(), rates, CoroutineScope(io), io)
        player =
            ReaderPlayer(
                Looper.getMainLooper(),
                internal,
                dependencies,
                { index -> "Chapter ${index + 1}" },
                SystemClock::elapsedRealtime,
            )
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun `presents one item per chapter with the book's metadata and estimated durations`() {
        open()
        advance(player).untilState(Player.STATE_READY)

        val window = player.currentTimeline.getWindow(1, Timeline.Window())
        assertThat(player.currentTimeline.windowCount).isEqualTo(2)
        assertThat(window.mediaItem.mediaId).isEqualTo("$BOOK_ID/1")
        assertThat(window.durationMs).isEqualTo(clock.millisFor(texts[1].length))
        assertThat(player.mediaMetadata.title.toString()).isEqualTo("Dom Casmurro")
        assertThat(player.mediaMetadata.artist.toString()).isEqualTo("Machado de Assis")
        assertThat(player.mediaMetadata.subtitle.toString()).isEqualTo("Capítulo 1")
    }

    @Test
    fun `plays every segment and continues into the next chapter until the end of the book`() {
        open()
        player.play()
        advance(player).untilState(Player.STATE_READY)

        runMainLooperUntil { player.currentMediaItemIndex == 1 }
        advance(player).untilState(Player.STATE_ENDED)

        val segments = texts.sumOf { planner.plan(it).size }
        assertThat(engine.spoken).hasSize(segments)
        assertThat(player.currentMediaItemIndex).isEqualTo(1)
        assertThat(content.savedPositions.last()).isEqualTo(ReadingPosition(1, texts[1].length))
    }

    @Test
    fun `resumes at the start of the segment that holds the saved position`() {
        val segments = planner.plan(texts[0])
        content.book = book(texts, position = ReadingPosition(0, segments[2].start + 5))

        open()
        advance(player).untilState(Player.STATE_READY)

        assertThat(player.currentPosition).isEqualTo(clock.millisFor(segments[2].start))
        assertThat(engine.spoken.first()).isEqualTo(texts[0].substring(segments[2].start, segments[2].end))
    }

    @Test
    fun `pausing saves the position reached and playing continues from it`() {
        open()
        player.play()
        advance(player).untilState(Player.STATE_READY)
        runMainLooperUntil { player.currentPosition > clock.millisFor(100) }

        player.pause()

        val saved = content.savedPositions.last()
        assertThat(saved.chapterIndex).isEqualTo(0)
        assertThat(saved.charOffset).isAtLeast(100)
        assertThat(clock.millisFor(saved.charOffset)).isWithin(POSITION_TOLERANCE_MS).of(player.currentPosition)
        player.play()
        runMainLooperUntil { player.isPlaying }
    }

    @Test
    fun `seeking back 15 seconds from the start of a chapter lands in the previous one`() {
        content.book = book(texts, position = ReadingPosition(1, 0))
        open()
        advance(player).untilState(Player.STATE_READY)

        player.seekBack()
        advance(player).untilState(Player.STATE_READY)

        val segments = planner.plan(texts[0])
        val expected = segments[planner.segmentAt(segments, texts[0].length - step)]
        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(player.currentPosition).isEqualTo(clock.millisFor(expected.start))
        assertThat(content.savedPositions.last()).isEqualTo(ReadingPosition(0, expected.start))
    }

    @Test
    fun `headset next and previous move 15 seconds instead of changing chapters`() {
        open()
        advance(player).untilState(Player.STATE_READY)

        player.seekToNext()
        advance(player).untilState(Player.STATE_READY)
        val segments = planner.plan(texts[0])
        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(player.currentPosition).isEqualTo(clock.millisFor(segments[planner.segmentAt(segments, step)].start))

        player.seekToPrevious()
        advance(player).untilState(Player.STATE_READY)
        // Back from less than 15 s in: clamped to the start of the book.
        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(player.currentPosition).isEqualTo(0)
    }

    @Test
    fun `seeking forward past the end of the book ends it at its last character`() {
        val lastSegment = planner.plan(texts[1]).last()
        content.book = book(texts, position = ReadingPosition(1, lastSegment.start))
        open()
        advance(player).untilState(Player.STATE_READY)

        player.seekForward()
        advance(player).untilState(Player.STATE_ENDED)

        assertThat(content.savedPositions.last()).isEqualTo(ReadingPosition(1, texts[1].length))
    }

    @Test
    fun `jumps to the start of a chapter and seeks within it`() {
        open()
        advance(player).untilState(Player.STATE_READY)

        player.seekTo(1, 0)
        advance(player).untilState(Player.STATE_READY)
        assertThat(player.currentMediaItemIndex).isEqualTo(1)
        assertThat(player.currentPosition).isEqualTo(0)

        player.seekTo(clock.millisFor(100))
        advance(player).untilState(Player.STATE_READY)
        val segments = planner.plan(texts[1])
        assertThat(player.currentPosition).isEqualTo(clock.millisFor(segments[planner.segmentAt(segments, 100)].start))
    }

    @Test
    fun `a speed change keeps the pitch and is saved`() {
        open()
        advance(player).untilState(Player.STATE_READY)

        player.setPlaybackSpeed(1.5f)

        assertThat(player.playbackParameters.speed).isEqualTo(1.5f)
        assertThat(internal.playbackParameters).isEqualTo(PlaybackParameters(1.5f))
        assertThat(content.savedSpeed).isEqualTo(1.5f)
    }

    @Test
    fun `waits in buffering when rendering falls behind and continues when audio is ready`() {
        val gate = CompletableDeferred<Unit>()
        engine.rendersBeforeGate = 2
        engine.gate = gate
        open()
        player.play()
        advance(player).untilState(Player.STATE_READY)

        advance(player).untilState(Player.STATE_BUFFERING)
        assertThat(player.playWhenReady).isTrue()

        gate.complete(Unit)
        advance(player).untilState(Player.STATE_READY)
        runMainLooperUntil { player.isPlaying }
    }

    @Test
    fun `losing audio focus pauses with that reason and saves the position`() {
        val reasons = mutableListOf<Int>()
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    reasons += reason
                }
            },
        )
        open()
        player.play()
        runMainLooperUntil { player.currentPosition > clock.millisFor(50) }
        val saves = content.savedPositions.size

        changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS)
        runMainLooperUntil { !player.playWhenReady }

        assertThat(reasons.last()).isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        assertThat(content.savedPositions.size).isGreaterThan(saves)
    }

    @Test
    fun `a phone call suppresses playback and it resumes when the call ends`() {
        open()
        player.play()
        runMainLooperUntil { player.isPlaying }

        changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        runMainLooperUntil {
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        }
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.isPlaying).isFalse()

        changeAudioFocus(AudioManager.AUDIOFOCUS_GAIN)
        runMainLooperUntil { player.isPlaying }
    }

    @Test
    fun `stopping saves the position and preparing again resumes there`() {
        val segments = planner.plan(texts[0])
        content.book = book(texts, position = ReadingPosition(0, segments[3].start))
        open()
        advance(player).untilState(Player.STATE_READY)

        player.stop()
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
        assertThat(content.savedPositions.last()).isEqualTo(ReadingPosition(0, segments[3].start))

        player.prepare()
        advance(player).untilState(Player.STATE_READY)
        assertThat(player.currentPosition).isEqualTo(clock.millisFor(segments[3].start))
    }

    @Test
    fun `without a voice it fails asking for one, and plays once a voice is installed`() {
        content.book = book(texts, voice = null)
        open()

        val error = advance(player).untilPlayerError()
        assertThat(error.errorCode).isEqualTo(PlaybackErrors.NO_VOICE)
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)

        content.book = book(texts)
        player.prepare()
        advance(player).untilState(Player.STATE_READY)
        assertThat(content.loads).isEqualTo(2)
    }

    @Test
    fun `a speech engine failure is reported as a typed error`() {
        engine.failure = SynthesisFailure.RENDER_ERROR
        open()

        assertThat(advance(player).untilPlayerError().errorCode).isEqualTo(PlaybackErrors.SPEECH_FAILED)
    }

    @Test
    fun `an unknown book is reported as unavailable`() {
        player.setMediaItem(MediaItem.Builder().setMediaId("00000000-0000-0000-0000-00000000000f").build())
        player.prepare()

        assertThat(advance(player).untilPlayerError().errorCode).isEqualTo(PlaybackErrors.BOOK_UNAVAILABLE)
    }

    private fun open() {
        player.setMediaItem(MediaItem.Builder().setMediaId(BOOK_ID).build())
        player.prepare()
    }

    /** Delivers an audio focus change the way the platform does, on the internal player's thread. */
    private fun changeAudioFocus(focusChange: Int) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        Handler(internal.playbackLooper).post {
            shadowOf(audioManager).lastAudioFocusRequest.listener.onAudioFocusChange(focusChange)
        }
    }

    private companion object {
        /** A character of the default rate lasts about 69 ms. */
        const val POSITION_TOLERANCE_MS = 100L
    }
}
