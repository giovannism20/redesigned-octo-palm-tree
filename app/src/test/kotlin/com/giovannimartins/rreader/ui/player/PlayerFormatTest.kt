package com.giovannimartins.rreader.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class PlayerFormatTest {
    @Test
    fun `durations show minutes and seconds, and hours when needed`() {
        assertThat(PlayerFormat.duration(0)).isEqualTo("0:00")
        assertThat(PlayerFormat.duration(65_999)).isEqualTo("1:05")
        assertThat(PlayerFormat.duration(3_723_000)).isEqualTo("1:02:03")
        assertThat(PlayerFormat.duration(-5_000)).isEqualTo("0:00")
    }

    @Test
    fun `speeds use the locale's decimal separator`() {
        val portuguese = Locale.forLanguageTag("pt-BR")

        assertThat(PlayerFormat.SPEEDS.map { PlayerFormat.speed(it, portuguese) })
            .containsExactly("0,75", "1,0", "1,25", "1,5", "1,75", "2,0")
            .inOrder()
        assertThat(PlayerFormat.speed(1.25f, Locale.US)).isEqualTo("1.25")
    }
}
