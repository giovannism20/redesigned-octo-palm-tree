package com.giovannimartins.rreader.playback

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The small rewind after a long pause, with a real internal ExoPlayer. Pauses happen while nothing plays and
 * resumes are paused again at once, so the segment the player restarts from can be checked exactly; the
 * facade's clock is advanced by hand (see [ReaderPlayerFixture]).
 */
@RunWith(RobolectricTestRunner::class)
class ReaderPlayerResumeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val texts = listOf(chapterText(0, 12), chapterText(1, 12))
    private val planner = SegmentPlanner()
    private val segments = planner.plan(texts[0])
    private lateinit var fixture: ReaderPlayerFixture
    private val player get() = fixture.player
    private val audioManager =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)

    @Before
    fun setUp() {
        fixture = ReaderPlayerFixture(folder.root, FakePlaybackContent(book(texts), texts))
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun `resuming after a long pause starts one segment earlier`() {
        openAt(ReadingPosition(0, segments[3].start + 5))
        player.pause()
        wait(6.minutes)

        resume()

        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 0, segments[2]))
    }

    @Test
    fun `a short pause resumes where it stopped`() {
        openAt(ReadingPosition(0, segments[3].start + 5))
        player.pause()
        wait(4.minutes)

        resume()

        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 0, segments[3]))
    }

    @Test
    fun `there is nothing to rewind at the start of the book`() {
        openAt(ReadingPosition.START)
        player.pause()
        wait(1.hours)

        resume()

        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 0, segments[0]))
    }

    @Test
    fun `a long pause followed by a seek does not rewind`() {
        openAt(ReadingPosition(0, segments[3].start))
        player.pause()
        wait(6.minutes)

        player.seekTo(1, 0)
        resume()

        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 1, planner.plan(texts[1]).first()))
    }

    @Test
    fun `a book left a while ago reopens a little earlier, one left recently where it stopped`() {
        openAt(ReadingPosition(0, segments[3].start + 5), pausedFor = 1.hours)
        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 0, segments[2]))

        player.release()
        setUp()
        openAt(ReadingPosition(0, segments[3].start + 5), pausedFor = 2.minutes)
        assertThat(spoken()).isEqualTo(SpokenSegment(BOOK_ID, 0, segments[3]))
    }

    @Test
    fun `listening goes back a little when a long phone call ends`() {
        openAt(ReadingPosition(0, segments[3].start))
        player.play()
        runMainLooperUntil { player.isPlaying && shadowOf(audioManager).lastAudioFocusRequest != null }

        changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        runMainLooperUntil {
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        }
        val interrupted = segments.indexOf(checkNotNull(spoken()).range)
        val saves = fixture.content.savedPositions.size
        wait(10.minutes)
        changeAudioFocus(AudioManager.AUDIOFOCUS_GAIN)

        val previous = ReadingPosition(0, segments[interrupted - 1].start)
        runMainLooperUntil { previous in fixture.content.savedPositions.drop(saves) }
    }

    private fun openAt(
        position: ReadingPosition,
        pausedFor: Duration? = null,
    ) {
        fixture.content.book = book(texts, position = position, pausedFor = pausedFor)
        fixture.open()
        advance(player).untilState(Player.STATE_READY)
    }

    /** Plays and pauses again at once, then waits until the player is ready where it restarted. */
    private fun resume() {
        player.play()
        player.pause()
        advance(player).untilState(Player.STATE_READY)
    }

    private fun wait(duration: Duration) = fixture.clock.advanceTime(duration.inWholeMilliseconds)

    private fun spoken(): SpokenSegment? = fixture.status.spokenSegment.value

    /** Delivers an audio focus change the way the platform does, on the internal player's thread. */
    private fun changeAudioFocus(focusChange: Int) {
        Handler(fixture.internal.playbackLooper).post {
            shadowOf(audioManager).lastAudioFocusRequest.listener.onAudioFocusChange(focusChange)
        }
    }
}
