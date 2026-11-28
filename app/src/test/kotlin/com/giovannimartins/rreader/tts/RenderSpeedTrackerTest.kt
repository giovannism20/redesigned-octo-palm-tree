package com.giovannimartins.rreader.tts

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenderSpeedTrackerTest {
    private val voice = "neural:dora"

    @Test
    fun `does not judge a voice before a few segments were measured`() {
        val tracker = testRenderSpeeds()

        repeat(RenderCost.TRUSTED_SAMPLES - 1) { tracker.record(voice, renderMillis = 20_000, speechMillis = 10_000) }

        assertThat(tracker.keepsUp(voice, 1f)).isNull()
        tracker.record(voice, renderMillis = 20_000, speechMillis = 10_000)
        assertThat(tracker.keepsUp(voice, 1f)).isFalse()
    }

    @Test
    fun `a voice keeps up when it renders comfortably faster than the listener hears`() {
        val tracker = testRenderSpeeds()

        // 0.7 s to render a second: enough at 1.0x and 1.25x (0.875), not at 1.5x (1.05).
        repeat(RenderCost.TRUSTED_SAMPLES) { tracker.record(voice, renderMillis = 7_000, speechMillis = 10_000) }

        assertThat(tracker.keepsUp(voice, 1f)).isTrue()
        assertThat(tracker.keepsUp(voice, 1.25f)).isTrue()
        assertThat(tracker.keepsUp(voice, 1.5f)).isFalse()
    }

    @Test
    fun `the cost follows the latest segments`() {
        val tracker = testRenderSpeeds()
        repeat(RenderCost.TRUSTED_SAMPLES) { tracker.record(voice, renderMillis = 20_000, speechMillis = 10_000) }

        // The phone cooled down, or the voice now renders at the listening speed.
        repeat(10) { tracker.record(voice, renderMillis = 3_000, speechMillis = 10_000) }

        assertThat(tracker.keepsUp(voice, 1.25f)).isTrue()
    }

    @Test
    fun `short segments are not measured`() {
        val tracker = testRenderSpeeds()

        repeat(5) { tracker.record(voice, renderMillis = 9_000, speechMillis = 1_000) }

        assertThat(tracker.costs.value).isEmpty()
    }

    @Test
    fun `costs are kept across restarts`() =
        runTest {
            val store = InMemoryRenderCostStore()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val first = testRenderSpeeds(store, scope)
            repeat(RenderCost.TRUSTED_SAMPLES) { first.record(voice, renderMillis = 15_000, speechMillis = 10_000) }

            val next = testRenderSpeeds(store, scope)

            assertThat(store.saved.getValue(voice)).isWithin(0.01).of(1.5)
            assertThat(next.keepsUp(voice, 1f)).isFalse()
        }
}
