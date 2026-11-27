package com.giovannimartins.rreader.textkit.estimate

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ReadingEstimatesTest {
    /** Three chapters, 350 characters in all. */
    private val lengths = listOf(100, 50, 200)

    @Nested
    inner class Progress {
        @Test
        fun `is the fraction of characters before the position`() {
            assertThat(ReadingEstimates.progress(ReadingPosition.START, lengths)).isEqualTo(0.0)
            assertThat(ReadingEstimates.progress(ReadingPosition(1, 10), lengths)).isWithin(TOLERANCE).of(110.0 / 350)
            assertThat(ReadingEstimates.progress(ReadingPosition(2, 200), lengths)).isEqualTo(1.0)
        }

        @Test
        fun `clamps positions outside the book`() {
            assertThat(ReadingEstimates.progress(ReadingPosition(0, 500), lengths)).isWithin(TOLERANCE).of(100.0 / 350)
            assertThat(ReadingEstimates.progress(ReadingPosition(5, 0), lengths)).isEqualTo(1.0)
        }

        @Test
        fun `is zero for a book without text`() {
            assertThat(ReadingEstimates.progress(ReadingPosition(1, 3), emptyList())).isEqualTo(0.0)
            assertThat(ReadingEstimates.progress(ReadingPosition(1, 3), listOf(0, 0))).isEqualTo(0.0)
        }

        @Test
        fun `rejects negative chapter lengths`() {
            assertThrows<IllegalArgumentException> { ReadingEstimates.progress(ReadingPosition.START, listOf(10, -1)) }
        }
    }

    @Nested
    inner class TimeLeft {
        @Test
        fun `counts the characters left in the book`() {
            assertThat(ReadingEstimates.remainingCharacters(ReadingPosition.START, lengths)).isEqualTo(350)
            assertThat(ReadingEstimates.remainingCharacters(ReadingPosition(1, 10), lengths)).isEqualTo(240)
            assertThat(ReadingEstimates.remainingCharacters(ReadingPosition(2, 200), lengths)).isEqualTo(0)
            assertThat(ReadingEstimates.remainingCharacters(ReadingPosition(9, 9), lengths)).isEqualTo(0)
        }

        @Test
        fun `divides the characters left by the rate and the speed`() {
            val rate = SpeakingRate.DEFAULT

            assertThat(ReadingEstimates.remainingDuration(1_450, rate, speed = 1f)).isEqualTo(100.seconds)
            assertThat(ReadingEstimates.remainingDuration(1_450, rate, speed = 2f)).isEqualTo(50.seconds)
            assertThat(ReadingEstimates.remainingDuration(87_000, rate, speed = 1f)).isEqualTo(100.minutes)
        }

        @Test
        fun `is zero when nothing is left`() {
            assertThat(ReadingEstimates.remainingDuration(0, SpeakingRate.DEFAULT, speed = 1f)).isEqualTo(Duration.ZERO)
            assertThat(ReadingEstimates.remainingDuration(-10, SpeakingRate.DEFAULT, speed = 1f))
                .isEqualTo(Duration.ZERO)
        }

        @Test
        fun `rejects speeds that make no sense`() {
            assertThrows<IllegalArgumentException> { ReadingEstimates.remainingDuration(10, SpeakingRate.DEFAULT, 0f) }
            assertThrows<IllegalArgumentException> { ReadingEstimates.remainingDuration(10, SpeakingRate.DEFAULT, -1f) }
            assertThrows<IllegalArgumentException> {
                ReadingEstimates.charactersFor(15.seconds, SpeakingRate.DEFAULT, Float.NaN)
            }
        }
    }

    @Nested
    inner class CharactersFor {
        @Test
        fun `converts a duration into characters`() {
            // 15 s × 14.5 cps = 217.5, rounded half to even.
            assertThat(ReadingEstimates.charactersFor(15.seconds, SpeakingRate.DEFAULT, speed = 1f)).isEqualTo(218)
            assertThat(ReadingEstimates.charactersFor(15.seconds, SpeakingRate.DEFAULT, speed = 2f)).isEqualTo(435)
            assertThat(ReadingEstimates.charactersFor(Duration.ZERO, SpeakingRate.DEFAULT, speed = 1f)).isEqualTo(0)
        }

        @Test
        fun `gives opposite counts for opposite durations`() {
            assertThat(ReadingEstimates.charactersFor((-15).seconds, SpeakingRate.DEFAULT, speed = 1f)).isEqualTo(-218)
        }

        @Test
        fun `saturates for absurd durations`() {
            assertThat(ReadingEstimates.charactersFor(Duration.INFINITE, SpeakingRate.DEFAULT, speed = 1f))
                .isEqualTo(Int.MAX_VALUE)
            assertThat(ReadingEstimates.charactersFor(-Duration.INFINITE, SpeakingRate.DEFAULT, speed = 1f))
                .isEqualTo(Int.MIN_VALUE)
        }
    }

    @Nested
    inner class Shift {
        @Test
        fun `moves within a chapter`() {
            assertThat(ReadingEstimates.shift(ReadingPosition(0, 10), 20, lengths)).isEqualTo(ReadingPosition(0, 30))
            assertThat(ReadingEstimates.shift(ReadingPosition(2, 50), -20, lengths)).isEqualTo(ReadingPosition(2, 30))
        }

        @Test
        fun `moves forward into the next chapter`() {
            assertThat(ReadingEstimates.shift(ReadingPosition(0, 90), 20, lengths)).isEqualTo(ReadingPosition(1, 10))
            assertThat(ReadingEstimates.shift(ReadingPosition(0, 90), 10, lengths)).isEqualTo(ReadingPosition(1, 0))
            assertThat(ReadingEstimates.shift(ReadingPosition(0, 90), 70, lengths)).isEqualTo(ReadingPosition(2, 10))
        }

        @Test
        fun `moves backward into the previous chapter`() {
            assertThat(ReadingEstimates.shift(ReadingPosition(1, 10), -30, lengths)).isEqualTo(ReadingPosition(0, 80))
            assertThat(ReadingEstimates.shift(ReadingPosition(2, 5), -5, lengths)).isEqualTo(ReadingPosition(2, 0))
            assertThat(ReadingEstimates.shift(ReadingPosition(2, 5), -6, lengths)).isEqualTo(ReadingPosition(1, 49))
        }

        @Test
        fun `clamps at the start and the end of the book`() {
            assertThat(ReadingEstimates.shift(ReadingPosition(1, 10), -1_000, lengths)).isEqualTo(ReadingPosition.START)
            assertThat(
                ReadingEstimates.shift(ReadingPosition(1, 10), 1_000, lengths),
            ).isEqualTo(ReadingPosition(2, 200))
            assertThat(ReadingEstimates.shift(ReadingPosition(7, 3), -10, lengths)).isEqualTo(ReadingPosition(2, 190))
            assertThat(ReadingEstimates.shift(ReadingPosition(1, 10), Int.MAX_VALUE, lengths))
                .isEqualTo(ReadingPosition(2, 200))
            assertThat(ReadingEstimates.shift(ReadingPosition(1, 10), Int.MIN_VALUE, lengths))
                .isEqualTo(ReadingPosition.START)
        }

        @Test
        fun `skips empty chapters`() {
            val withEmpty = listOf(100, 0, 50)

            assertThat(ReadingEstimates.shift(ReadingPosition(0, 95), 10, withEmpty)).isEqualTo(ReadingPosition(2, 5))
            assertThat(ReadingEstimates.shift(ReadingPosition(2, 0), -1, withEmpty)).isEqualTo(ReadingPosition(0, 99))
        }

        @Test
        fun `returns the start for a book without chapters`() {
            assertThat(ReadingEstimates.shift(ReadingPosition(3, 7), 15, emptyList())).isEqualTo(ReadingPosition.START)
        }

        @Test
        fun `undoes a 15 second jump with the opposite jump`() {
            val jump = ReadingEstimates.charactersFor(15.seconds, SpeakingRate.DEFAULT, speed = 1.25f)
            val position = ReadingPosition(1, 20)
            val book = listOf(400, 300, 500)

            val forward = ReadingEstimates.shift(position, jump, book)
            val back = ReadingEstimates.shift(forward, -jump, book)

            assertThat(forward).isEqualTo(ReadingPosition(1, 20 + jump))
            assertThat(back).isEqualTo(position)
        }
    }

    private companion object {
        const val TOLERANCE = 1e-12
    }
}
