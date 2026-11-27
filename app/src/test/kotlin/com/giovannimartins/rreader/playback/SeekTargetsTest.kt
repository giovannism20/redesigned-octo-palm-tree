package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SeekTargetsTest {
    /** 10 characters per second: 15 s are 150 characters. */
    private val clock = ChapterClock(SpeakingRate(charsPerSecond = 10.0))
    private val lengths = listOf(1_000, 100, 2_000)

    @Test
    fun `seeking back and forward moves 15 seconds of characters within a chapter`() {
        val from = ReadingPosition(0, 500)

        assertThat(offset(from, -15)).isEqualTo(ReadingPosition(0, 350))
        assertThat(offset(from, 15)).isEqualTo(ReadingPosition(0, 650))
    }

    @Test
    fun `seeking crosses chapter boundaries, skipping short chapters`() {
        assertThat(offset(ReadingPosition(2, 20), -5)).isEqualTo(ReadingPosition(1, 70))
        assertThat(offset(ReadingPosition(2, 120), -30)).isEqualTo(ReadingPosition(0, 920))
        assertThat(offset(ReadingPosition(0, 900), 15)).isEqualTo(ReadingPosition(1, 50))
    }

    @Test
    fun `seeking is clamped to the start and the end of the book`() {
        assertThat(offset(ReadingPosition(0, 100), -15)).isEqualTo(ReadingPosition.START)
        assertThat(offset(ReadingPosition(2, 1_950), 15)).isEqualTo(ReadingPosition(2, 2_000))
    }

    @Test
    fun `a forward seek always leaves the span being listened to`() {
        // The current segment's span ends at 800, beyond the 15 s target of 650.
        assertThat(offset(ReadingPosition(0, 500), 15, spanEnd = 800)).isEqualTo(ReadingPosition(0, 800))
        // A span that ends with its chapter moves on to the next chapter.
        assertThat(offset(ReadingPosition(0, 950), 1, spanEnd = 1_000)).isEqualTo(ReadingPosition(1, 0))
        // A target beyond the span is kept, and backward seeks ignore the span.
        assertThat(offset(ReadingPosition(0, 500), 15, spanEnd = 600)).isEqualTo(ReadingPosition(0, 650))
        assertThat(offset(ReadingPosition(0, 500), -15, spanEnd = 800)).isEqualTo(ReadingPosition(0, 350))
    }

    @Test
    fun `seeking within a chapter stays inside it`() {
        assertThat(SeekTargets.inChapter(0, 10_000, lengths, clock)).isEqualTo(ReadingPosition(0, 100))
        assertThat(SeekTargets.inChapter(1, 60_000, lengths, clock)).isEqualTo(ReadingPosition(1, 99))
        assertThat(SeekTargets.inChapter(2, -5, lengths, clock)).isEqualTo(ReadingPosition(2, 0))
        assertThat(SeekTargets.inChapter(3, 0, lengths, clock)).isNull()
    }

    private fun offset(
        from: ReadingPosition,
        seconds: Int,
        spanEnd: Int? = null,
    ): ReadingPosition = SeekTargets.offset(from, seconds.seconds, lengths, clock, spanEnd)
}
