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

    @Test
    fun `control characters become spaces and invisible format characters are dropped`() {
        assertThat(PhonemizerText.clean("um\tdois\u0007três\nquatro")).isEqualTo("um dois três quatro")
        assertThat(PhonemizerText.clean("anti\u00ADgo\u200B, casa\uFEFF")).isEqualTo("antigo, casa")
    }

    @Test
    fun `a run of characters without a space is split before espeak-ng gets it`() {
        val longest = "pneumoultramicroscopicossilicovulcanoconiótico"
        val cleaned = PhonemizerText.clean("x".repeat(100) + " $longest")

        assertThat(
            cleaned.split(' ').map(String::length),
        ).containsExactly(40, 40, 20, 40, longest.length - 40).inOrder()
    }
}
