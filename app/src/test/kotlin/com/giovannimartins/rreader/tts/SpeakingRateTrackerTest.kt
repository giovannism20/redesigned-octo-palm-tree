package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingRateTrackerTest {
    private val store = InMemorySpeakingRateStore()

    @Test
    fun `unmeasured voices use the default rate`() =
        runTest {
            val tracker = tracker()

            assertThat(tracker.rateFor("voice")).isEqualTo(SpeakingRate.DEFAULT)
            assertThat(tracker.rateFor(null)).isEqualTo(SpeakingRate.DEFAULT)
        }

    @Test
    fun `rendered segments move the voice's rate towards what was measured`() =
        runTest {
            val tracker = tracker()

            tracker.record("voice", chars = 200, duration = 10.seconds)

            val expected = SpeakingRate.DEFAULT.updatedWith(200, 10.seconds)
            assertThat(tracker.rateFor("voice")).isEqualTo(expected)
            assertThat(tracker.rateFor("other")).isEqualTo(SpeakingRate.DEFAULT)
        }

    @Test
    fun `short segments do not count because silence dominates them`() =
        runTest {
            val tracker = tracker()

            tracker.record("voice", chars = 20, duration = 3.seconds)
            runCurrent()

            assertThat(tracker.rateFor("voice")).isEqualTo(SpeakingRate.DEFAULT)
            assertThat(store.saves).isEqualTo(0)
        }

    @Test
    fun `measured rates are saved right away, then at most every half minute`() =
        runTest {
            val tracker = tracker()

            tracker.record("voice", chars = 200, duration = 10.seconds)
            runCurrent()
            assertThat(store.saves).isEqualTo(1)
            assertThat(store.saved.rateFor("voice")).isEqualTo(tracker.rateFor("voice"))
            assertThat(store.saved.lastVoiceId).isEqualTo("voice")

            tracker.record("voice", chars = 200, duration = 12.seconds)
            tracker.record("other", chars = 300, duration = 20.seconds)
            advanceTimeBy(29.seconds)
            runCurrent()
            assertThat(store.saves).isEqualTo(1)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertThat(store.saves).isEqualTo(2)
            assertThat(store.saved.rateFor("voice")).isEqualTo(tracker.rateFor("voice"))
            assertThat(store.saved.rateFor("other")).isEqualTo(tracker.rateFor("other"))
            assertThat(store.saved.lastVoiceId).isEqualTo("other")
        }

    @Test
    fun `restores the rates of earlier sessions without losing newer measurements`() =
        runTest {
            store.saved = SpeakingRates(mapOf("old" to SpeakingRate(12.0), "voice" to SpeakingRate(20.0)), "old")
            val tracker = tracker()
            tracker.record("voice", chars = 200, duration = 10.seconds)

            tracker.restore()
            tracker.restore()

            assertThat(tracker.rateFor("old")).isEqualTo(SpeakingRate(12.0))
            assertThat(tracker.rateFor("voice")).isEqualTo(SpeakingRate.DEFAULT.updatedWith(200, 10.seconds))
        }

    @Test
    fun `the rates start from the restored ones`() =
        runTest {
            store.saved = SpeakingRates(mapOf("voice" to SpeakingRate(16.0)), "voice")

            val rates = tracker().rates.first()

            assertThat(rates.rateFor("voice")).isEqualTo(SpeakingRate(16.0))
            assertThat(rates.lastVoiceId).isEqualTo("voice")
        }

    private fun TestScope.tracker() = SpeakingRateTracker(store, backgroundScope)
}
