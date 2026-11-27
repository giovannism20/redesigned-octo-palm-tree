package com.giovannimartins.rreader.textkit.estimate

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SpeakingRateTest {
    @Test
    fun `defaults to typical Portuguese narration`() {
        assertThat(SpeakingRate.DEFAULT.charsPerSecond).isEqualTo(14.5)
    }

    @Test
    fun `blends a sample into the rate with an exponential moving average`() {
        // 290 characters in 10 s is 29 cps: 14.5 + 0.2 × (29 − 14.5) = 17.4.
        val updated = SpeakingRate.DEFAULT.updatedWith(sampleChars = 290, sampleDuration = 10.seconds)

        assertThat(updated.charsPerSecond).isWithin(TOLERANCE).of(17.4)
    }

    @Test
    fun `converges to the rate of a steady voice`() {
        var rate = SpeakingRate.DEFAULT

        repeat(100) { rate = rate.updatedWith(sampleChars = 180, sampleDuration = 12.seconds) }

        assertThat(rate.charsPerSecond).isWithin(TOLERANCE).of(15.0)
    }

    @Test
    fun `weighs the sample as asked`() {
        val rate = SpeakingRate(10.0)

        assertThat(rate.updatedWith(150, 10.seconds, weight = 0.5).charsPerSecond).isWithin(TOLERANCE).of(12.5)
        assertThat(rate.updatedWith(150, 10.seconds, weight = 1.0).charsPerSecond).isWithin(TOLERANCE).of(15.0)
        assertThat(rate.updatedWith(150, 10.seconds, weight = 0.0)).isEqualTo(rate)
        assertThat(rate.updatedWith(48, 3_200.milliseconds).charsPerSecond).isWithin(TOLERANCE).of(11.0)
    }

    @Test
    fun `ignores degenerate samples`() {
        val rate = SpeakingRate(14.5)

        assertThat(rate.updatedWith(0, 10.seconds)).isEqualTo(rate)
        assertThat(rate.updatedWith(-5, 10.seconds)).isEqualTo(rate)
        assertThat(rate.updatedWith(100, Duration.ZERO)).isEqualTo(rate)
        assertThat(rate.updatedWith(100, (-1).seconds)).isEqualTo(rate)
        assertThat(rate.updatedWith(100, Duration.INFINITE)).isEqualTo(rate)
        assertThat(rate.updatedWith(5_000, 1.seconds)).isEqualTo(rate)
        assertThat(rate.updatedWith(1, 10.seconds)).isEqualTo(rate)
    }

    @Test
    fun `rejects rates and weights that make no sense`() {
        assertThrows<IllegalArgumentException> { SpeakingRate(0.0) }
        assertThrows<IllegalArgumentException> { SpeakingRate(-1.0) }
        assertThrows<IllegalArgumentException> { SpeakingRate(Double.NaN) }
        assertThrows<IllegalArgumentException> { SpeakingRate(Double.POSITIVE_INFINITY) }
        assertThrows<IllegalArgumentException> { SpeakingRate.DEFAULT.updatedWith(100, 10.seconds, weight = 1.5) }
        assertThrows<IllegalArgumentException> { SpeakingRate.DEFAULT.updatedWith(100, 10.seconds, weight = -0.1) }
        assertThrows<IllegalArgumentException> { SpeakingRate.DEFAULT.updatedWith(100, 10.seconds, Double.NaN) }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
