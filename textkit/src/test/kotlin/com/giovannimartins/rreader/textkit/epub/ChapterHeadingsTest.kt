package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChapterHeadingsTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "CAPÍTULO I",
            "CAPITULO PRIMEIRO",
            "Capítulo 3: A viagem",
            "PRIMEIRA PARTE",
            "Prólogo",
            "I",
            "XII.",
            "12",
            "IV — A partida",
            "I A LIÇÃO DE VIOLÃO",
            "VI. SMIERDIAKÓV",
            "CCI",
        ],
    )
    fun `recognizes headings that open a chapter`(text: String) {
        assertThat(ChapterHeadings.isChapterHeading(text)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "D. Gloria.",
            "Do titulo.",
            "DA ACADEMIA BRAZILEIRA",
            "1888",
            "7, RUA DO OUVIDOR, 7",
            "MIL",
            "CIVIL",
            "VIDA",
            "iv",
            "CD",
            "XII — um título comprido demais para ser o de um capítulo de livro, que passa da conta",
        ],
    )
    fun `does not take other headings for chapter starts`(text: String) {
        assertThat(ChapterHeadings.isChapterHeading(text)).isFalse()
    }
}
