package com.giovannimartins.rreader.tts.neural

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhonemizerTextTest {
    @Test
    fun `Portuguese text, old spellings and punctuation stay as they are`() {
        val text = "— Pòde ser, sòmente è assim: «Andarahy», Shakespeare & Wagner, 1899."

        assertThat(PhonemizerText.clean(text)).isEqualTo(text)
    }

    @Test
    fun `digits, symbols, emoji and combining accents stay as they are`() {
        val text = "Custa $ 5 • # + = > % ™ © € ½ ª º § 😀 é"

        assertThat(PhonemizerText.clean(text)).isEqualTo(text)
    }

    @Test
    fun `letters of other alphabets become spaces`() {
        assertThat(PhonemizerText.clean("Logos λόγος, Привет e 中文.")).isEqualTo("Logos      ,        e   .")
    }
}
