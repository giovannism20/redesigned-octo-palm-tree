package com.giovannimartins.rreader.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakeClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Drives the timer with a fake clock that only moves when the test says so. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SleepTimerTest {
    private val clock = FakeClock(false)
    private val host = FakeHost()
    private val timer = SleepTimer(clock, Looper.getMainLooper(), host)

    @Test
    fun `counts down only while audio plays and shows every second`() {
        timer.start(SleepTimerSetting.After(5.minutes))
        advance(2.minutes)
        assertThat(host.states.last()).isEqualTo(SleepTimerState.After(5.minutes))

        play(true)
        advance(1_500.milliseconds)
        assertThat(host.states.last()).isEqualTo(SleepTimerState.After(4.minutes + 59.seconds))

        play(false)
        advance(10.minutes)
        assertThat(host.states.last()).isEqualTo(SleepTimerState.After(4.minutes + 59.seconds))
        assertThat(host.events).doesNotContain("sleep")
    }

    @Test
    fun `fades out over the last ten seconds, then pauses and restores the volume`() {
        play(true)
        timer.start(SleepTimerSetting.After(30.seconds))

        advance(20.seconds)
        assertThat(host.level).isEqualTo(1f)
        advance(5.seconds)
        assertThat(host.level).isWithin(VOLUME_TOLERANCE).of(0.5f)
        advance(4_900.milliseconds)
        assertThat(host.level).isWithin(VOLUME_TOLERANCE).of(0.01f)
        advance(100.milliseconds)

        assertThat(host.events.takeLast(2)).containsExactly("sleep", "volume 1.0").inOrder()
        assertThat(host.states.last()).isEqualTo(SleepTimerState.Off)
        assertThat(timer.isEndOfChapter).isFalse()
    }

    @Test
    fun `pausing during the fade keeps the time left for when listening goes on`() {
        play(true)
        timer.start(SleepTimerSetting.After(15.seconds))
        advance(10.seconds)
        play(false)
        advance(1.minutes)
        assertThat(host.events).doesNotContain("sleep")

        play(true)
        advance(5.seconds)

        assertThat(host.events).contains("sleep")
    }

    @Test
    fun `cancelling turns the timer off and restores the volume`() {
        play(true)
        timer.start(SleepTimerSetting.After(12.seconds))
        advance(7.seconds)
        assertThat(host.level).isLessThan(1f)

        timer.cancel()
        advance(1.minutes)

        assertThat(host.level).isEqualTo(1f)
        assertThat(host.states.last()).isEqualTo(SleepTimerState.Off)
        assertThat(host.events).doesNotContain("sleep")
    }

    @Test
    fun `at the end of the chapter it fades with the audio left and pauses when the chapter ends`() {
        play(true)
        host.chapterLeft = 60_000
        timer.start(SleepTimerSetting.EndOfChapter)
        assertThat(host.states.last()).isEqualTo(SleepTimerState.EndOfChapter)
        assertThat(timer.isEndOfChapter).isTrue()

        host.chapterLeft = 4_000
        advance(1.seconds)
        assertThat(host.level).isWithin(VOLUME_TOLERANCE).of(0.4f)

        host.chapterLeft = null
        advance(1.seconds)
        assertThat(host.level).isEqualTo(1f)

        timer.onChapterEnded()
        assertThat(host.events.last()).isEqualTo("sleep")
        assertThat(host.states.last()).isEqualTo(SleepTimerState.Off)
    }

    @Test
    fun `the end of a chapter does not stop a timer that counts minutes`() {
        play(true)
        timer.start(SleepTimerSetting.After(5.minutes))

        timer.onChapterEnded()

        assertThat(host.events).doesNotContain("sleep")
        assertThat(host.states.last()).isInstanceOf(SleepTimerState.After::class.java)
    }

    private fun play(playing: Boolean) {
        host.isPlaying = playing
        timer.onPlayingChanged()
    }

    /** Moves the fake clock by [duration] and runs the ticks that became due. */
    private fun advance(duration: Duration) {
        ShadowLooper.idleMainLooper()
        clock.advanceTime(duration.inWholeMilliseconds)
        ShadowLooper.idleMainLooper()
    }

    private class FakeHost : SleepTimer.Host {
        override var isPlaying = false
        var chapterLeft: Long? = null
        var level = 1f
        val events = mutableListOf<String>()
        val states = mutableListOf<SleepTimerState>()

        override fun chapterAudioLeftMillis(): Long? = chapterLeft

        override fun setVolume(volume: Float) {
            level = volume
            events += "volume $volume"
        }

        override fun sleep() {
            events += "sleep"
        }

        override fun onStateChanged(state: SleepTimerState) {
            states += state
        }
    }

    private companion object {
        const val VOLUME_TOLERANCE = 0.02f
    }
}
