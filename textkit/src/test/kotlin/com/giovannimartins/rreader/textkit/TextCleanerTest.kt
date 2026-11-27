package com.giovannimartins.rreader.textkit

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TextCleanerTest {
    @Test
    fun `collapses every kind of whitespace into single spaces and trims the ends`() {
        val raw = " \n\tUm\u00A0dois\u202Ftrês\u2007quatro\u3000cinco\u2028seis \r\n "

        assertThat(TextCleaner.clean(raw)).isEqualTo("Um dois três quatro cinco seis")
    }

    @Test
    fun `removes soft hyphens, zero-width and control characters`() {
        val raw = "\uFEFFhi\u00ADfe\u00ADni\u00ADza\u00ADção zero\u200Bwidth\u200C\u200D\u2060 sino\u0007 fim\u0085"

        assertThat(TextCleaner.clean(raw)).isEqualTo("hifenização zerowidth sino fim")
    }

    @Test
    fun `composes accents so equal words have equal characters`() {
        val decomposed = "Corac\u0327a\u0303o e cafe\u0301"

        assertThat(TextCleaner.clean(decomposed)).isEqualTo("Coração e café")
        assertThat(TextCleaner.clean(decomposed)).hasLength("Coração e café".length)
    }

    @Test
    fun `keeps punctuation and dialogue dashes`() {
        val dialogue = "— Não sei — disse ela. «Será?» Pôr-do-sol… (fim)!"

        assertThat(TextCleaner.clean(dialogue)).isEqualTo(dialogue)
    }
}
