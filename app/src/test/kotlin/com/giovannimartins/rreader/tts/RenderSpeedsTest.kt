package com.giovannimartins.rreader.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RenderSpeedsTest {
    @Test
    fun `renders at the player's steps of a quarter`() {
        assertThat(RenderSpeeds.of(1f)).isEqualTo(1f)
        assertThat(RenderSpeeds.of(1.25f)).isEqualTo(1.25f)
        assertThat(RenderSpeeds.of(1.3f)).isEqualTo(1.25f)
        assertThat(RenderSpeeds.of(1.4f)).isEqualTo(1.5f)
    }

    @Test
    fun `stays within the speeds the player offers`() {
        assertThat(RenderSpeeds.of(0.5f)).isEqualTo(0.75f)
        assertThat(RenderSpeeds.of(3f)).isEqualTo(2f)
        assertThat(RenderSpeeds.ALL).containsExactly(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).inOrder()
    }

    @Test
    fun `looks for cached audio at the closest speeds first`() {
        val order = RenderSpeeds.lookupOrder(1.25f)

        assertThat(order.first()).isEqualTo(1.25f)
        assertThat(order.subList(1, 3)).containsExactly(1f, 1.5f)
        assertThat(order).containsExactlyElementsIn(RenderSpeeds.ALL)
    }
}
