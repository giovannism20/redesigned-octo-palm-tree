package com.giovannimartins.rreader.textkit.layout

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HeadingsTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "CAPÍTULO IV",
            "CAPITULO 12",
            "Capítulo 3: A viagem",
            "Capítulo 3 — A viagem",
            "Capítulo 3 A viagem",
            "Capítulo I. A PARTIDA",
            "Capítulo Primeiro",
            "Capítulo Único",
            "PARTE PRIMEIRA",
            "Parte Dois",
            "Livro II",
            "Canto I",
            "Ato 2",
            "Tomo III",
            "Chapter 1",
            "PRIMEIRA PARTE",
            "Segundo livro",
            "Prólogo",
            "EPÍLOGO",
            "Introdução:",
            "Nota do autor",
        ],
    )
    fun `recognizes headings by their wording`(text: String) {
        assertThat(Headings.isNamedHeading(text)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "capítulo 3",
            "Capítulo 3 do regulamento",
            "Capítulo 1 ........ 5",
            "Capítulo 1 … 5",
            "Capítulo 3   45",
            "Capítulos",
            "Ato contínuo, saiu",
            "Livro de receitas",
            "Parte da manhã",
            "Parte MIL",
            "Introdução ao estudo",
            "Capítulo 1 — uma linha muito comprida que passa do tamanho de qualquer título de capítulo de livro",
        ],
    )
    fun `does not take other lines for headings`(text: String) {
        assertThat(Headings.isNamedHeading(text)).isFalse()
    }
}
