package com.giovannimartins.rreader.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakeClock
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ResumeRewindTest {
    private val lengths = listOf(500, 400)

    @Test
    fun `only pauses longer than five minutes rewind`() {
        assertThat(ResumeRewind.isLong(5.minutes)).isFalse()
        assertThat(ResumeRewind.isLong(5.minutes + 1.milliseconds)).isTrue()
    }

    @Test
    fun `rewinds one segment when that is less than the most text heard again`() {
        val target = ResumeRewind.target(ReadingPosition(0, 200), ReadingPosition(0, 150), 145, lengths)

        assertThat(target).isEqualTo(ReadingPosition(0, 150))
    }

    @Test
    fun `rewinds at most the given characters when the previous segment is long`() {
        val target = ResumeRewind.target(ReadingPosition(0, 300), ReadingPosition(0, 20), 145, lengths)

        assertThat(target).isEqualTo(ReadingPosition(0, 155))
    }

    @Test
    fun `rewinds into the previous chapter and never before the start of the book`() {
        val acrossChapters = ResumeRewind.target(ReadingPosition(1, 30), ReadingPosition(0, 470), 145, lengths)
        val nearStart = ResumeRewind.target(ReadingPosition(0, 40), ReadingPosition.START, 145, lengths)

        assertThat(acrossChapters).isEqualTo(ReadingPosition(0, 470))
        assertThat(nearStart).isEqualTo(ReadingPosition.START)
    }

    @Test
    fun `without a previous segment the position stays`() {
        assertThat(ResumeRewind.target(ReadingPosition(0, 12), null, 145, lengths)).isEqualTo(ReadingPosition(0, 12))
    }

    @Test
    fun `the pause clock tells long pauses from short ones and forgets them`() {
        val clock = FakeClock(0L)
        val pauses = PauseClock(clock)

        pauses.stopped()
        clock.advanceTime(4.minutes.inWholeMilliseconds)
        pauses.stopped()
        clock.advanceTime(2.minutes.inWholeMilliseconds)
        assertThat(pauses.resumesAfterLongPause()).isTrue()
        assertThat(pauses.resumesAfterLongPause()).isFalse()

        pauses.stopped()
        clock.advanceTime(10.minutes.inWholeMilliseconds)
        pauses.forget()
        assertThat(pauses.resumesAfterLongPause()).isFalse()

        pauses.stopped()
        clock.advanceTime(1.minutes.inWholeMilliseconds)
        assertThat(pauses.resumesAfterLongPause()).isFalse()
    }
}
