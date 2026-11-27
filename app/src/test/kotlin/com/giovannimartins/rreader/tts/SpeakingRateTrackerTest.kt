package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SpeakingRateTrackerTest {
    private val tracker = SpeakingRateTracker()

    @Test
    fun `unmeasured voices use the default rate`() {
        assertThat(tracker.rateFor("voice")).isEqualTo(SpeakingRate.DEFAULT)
        assertThat(tracker.rateFor(null)).isEqualTo(SpeakingRate.DEFAULT)
    }

    @Test
    fun `rendered segments move the voice's rate towards what was measured`() {
        tracker.record("voice", chars = 200, duration = 10.seconds)

        val expected = SpeakingRate.DEFAULT.updatedWith(200, 10.seconds)
        assertThat(tracker.rateFor("voice")).isEqualTo(expected)
        assertThat(tracker.rateFor("other")).isEqualTo(SpeakingRate.DEFAULT)
    }

    @Test
    fun `short segments do not count because silence dominates them`() {
        tracker.record("voice", chars = 20, duration = 3.seconds)

        assertThat(tracker.rateFor("voice")).isEqualTo(SpeakingRate.DEFAULT)
    }
}
