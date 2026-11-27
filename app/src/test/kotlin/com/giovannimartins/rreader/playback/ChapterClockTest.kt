package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterClockTest {
    private val clock = ChapterClock(SpeakingRate(charsPerSecond = 10.0))

    @Test
    fun `characters take their speaking time at normal speed`() {
        assertThat(clock.millisFor(0)).isEqualTo(0)
        assertThat(clock.millisFor(25)).isEqualTo(2_500)
        assertThat(clock.millisFor(1_234_567)).isEqualTo(123_456_700)
    }

    @Test
    fun `time maps back to the characters spoken in it`() {
        assertThat(clock.charsFor(2_500)).isEqualTo(25)
        assertThat(clock.charsFor(-15_000)).isEqualTo(-150)
        assertThat(clock.charsFor(clock.millisFor(777))).isEqualTo(777)
    }

    @Test
    fun `a slower voice makes chapters longer`() {
        val slower = ChapterClock(SpeakingRate(charsPerSecond = 5.0))

        assertThat(slower.millisFor(25)).isEqualTo(2 * clock.millisFor(25))
    }
}
