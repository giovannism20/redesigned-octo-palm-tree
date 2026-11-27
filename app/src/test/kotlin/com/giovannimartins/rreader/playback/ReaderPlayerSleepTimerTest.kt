package com.giovannimartins.rreader.playback

import androidx.media3.common.Player
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * The sleep timer inside the facade player, with a real internal ExoPlayer. The timer runs on the facade's
 * fake clock, which the tests advance in small steps while the audio plays on its own clock (see
 * [ReaderPlayerFixture]); it fades over one second instead of ten. Chapters of 30 rendered sentences last
 * about 11 seconds, so the audio does not run out before a timer of a few seconds ends.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderPlayerSleepTimerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val texts = listOf(chapterText(0, 30), chapterText(1, 30), chapterText(2, 30))
    private val planner = SegmentPlanner()
    private val chapterClock = ChapterClock(SpeakingRate.DEFAULT)
    private lateinit var fixture: ReaderPlayerFixture
    private val player get() = fixture.player

    @Before
    fun setUp() {
        fixture = ReaderPlayerFixture(folder.root, FakePlaybackContent(book(texts), texts))
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun `the timer fades the volume out, pauses after its time and saves the position`() {
        listen()
        val start = now()

        assertThat(player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.After(3.seconds)))).isTrue()
        assertThat(timerState()).isEqualTo(SleepTimerState.After(3.seconds))
        runUntil { fixture.internal.volume < 1f }
        assertThat(now() - start).isAtLeast(FADE_START_MS)
        val pausedAt = atPause(::now)
        runUntil { !player.playWhenReady }

        assertListened(pausedAt() - start, 3_000)
        assertThat(fixture.internal.volume).isEqualTo(1f)
        assertThat(timerState()).isEqualTo(SleepTimerState.Off)
        val saved = fixture.content.savedPositions.last()
        assertThat(chapterClock.millisFor(saved.charOffset)).isWithin(POSITION_TOLERANCE_MS).of(player.currentPosition)
    }

    @Test
    fun `the timer only counts while listening`() {
        fixture.open()
        advance(player).untilState(Player.STATE_READY)
        player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.After(2.seconds)))
        runUntil { now() > 60_000 }
        assertThat(timerState()).isEqualTo(SleepTimerState.After(2.seconds))

        player.play()
        advance(player).untilState(Player.STATE_READY)
        val start = now()
        val pausedAt = atPause(::now)
        runUntil { !player.playWhenReady }

        assertListened(pausedAt() - start, 2_000)
    }

    @Test
    fun `cancelling the timer restores the volume and listening goes on`() {
        listen()
        player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.After(3.seconds)))
        runUntil { fixture.internal.volume < 1f }
        val start = now()

        assertThat(player.handle(ReaderCommand.CancelSleepTimer)).isTrue()
        runUntil { now() - start > 3_000 }

        assertThat(fixture.internal.volume).isEqualTo(1f)
        assertThat(timerState()).isEqualTo(SleepTimerState.Off)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `at the end of the chapter the volume follows the audio left and listening pauses there`() {
        fixture.content.book = book(texts, position = ReadingPosition(0, planner.plan(texts[0]).last().start))
        listen()

        player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.EndOfChapter))
        val spokenAtPause = atPause { fixture.status.spokenSegment.value }

        // Less than half a second of the chapter is left, well inside the one-second fade.
        assertThat(timerState()).isEqualTo(SleepTimerState.EndOfChapter)
        assertThat(fixture.internal.volume).isLessThan(HALF)
        runUntil(stepMs = 10) { !player.playWhenReady }

        // The pause is asked for as soon as the next chapter starts.
        assertThat(spokenAtPause()).isEqualTo(SpokenSegment(BOOK_ID, 1, planner.plan(texts[1]).first()))
        assertThat(
            fixture.content.savedPositions
                .last()
                .chapterIndex,
        ).isEqualTo(1)
        assertThat(fixture.internal.volume).isEqualTo(1f)
        assertThat(timerState()).isEqualTo(SleepTimerState.Off)
    }

    @Test
    fun `the end of the book turns the timer off`() {
        fixture.content.book = book(texts, position = ReadingPosition(2, planner.plan(texts[2]).last().start))
        listen()

        player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.EndOfChapter))
        advance(player).untilState(Player.STATE_ENDED)

        assertThat(timerState()).isEqualTo(SleepTimerState.Off)
        assertThat(fixture.internal.volume).isEqualTo(1f)
    }

    @Test
    fun `the timer needs an open book`() {
        assertThat(player.handle(ReaderCommand.SetSleepTimer(SleepTimerSetting.EndOfChapter))).isFalse()
        assertThat(timerState()).isEqualTo(SleepTimerState.Off)
    }

    private fun listen() {
        fixture.open()
        player.play()
        advance(player).untilState(Player.STATE_READY)
    }

    /** Runs the main looper, moving the facade's clock by [stepMs] each time round, until [condition] holds. */
    private fun runUntil(
        stepMs: Long = STEP_MS,
        condition: () -> Boolean,
    ) = runMainLooperUntil {
        fixture.clock.advanceTime(stepMs)
        condition()
    }

    private fun now(): Long = fixture.clock.elapsedRealtime()

    /**
     * The timer ended after [elapsed] of the facade's clock for [listened] of listening: never earlier, and
     * later only by the moments the audio waited for rendering, which do not count.
     */
    private fun assertListened(
        elapsed: Long,
        listened: Long,
    ) {
        assertThat(elapsed).isIn(Range.closed(listened, listened + BUFFERING_SLACK_MS))
    }

    /** Records [value] at the moment the player is next paused; the result reads it once that happened. */
    private fun <T : Any> atPause(value: () -> T?): () -> T {
        var recorded: T? = null
        var paused = false
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (!playWhenReady && !paused) {
                        paused = true
                        recorded = value()
                    }
                }
            },
        )
        return { checkNotNull(recorded) }
    }

    private fun timerState(): SleepTimerState = fixture.status.sleepTimer.value

    private companion object {
        /** The one-second fade starts two seconds into a three-second timer. */
        const val FADE_START_MS = 2_000L
        const val STEP_MS = 50L
        const val BUFFERING_SLACK_MS = 10 * STEP_MS
        const val POSITION_TOLERANCE_MS = 100L
        const val HALF = 0.5f
    }
}
