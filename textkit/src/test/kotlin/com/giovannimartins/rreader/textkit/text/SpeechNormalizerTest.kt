package com.giovannimartins.rreader.textkit.text

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SpeechNormalizerTest {
    private val normalizer = SpeechNormalizer()

    private fun heading(text: String): String = normalizer.toSpeech(text, isHeading = true)

    private fun body(text: String): String = normalizer.toSpeech(text)

    @Nested
    inner class Headings {
        @ParameterizedTest
        @CsvSource(
            delimiter = '=',
            value = [
                "XII = 12",
                "I = 1",
                "XLVII. = 47.",
                "Capítulo XII = Capítulo 12",
                "CAPÍTULO XII = Capítulo 12",
                "CAPITULO XII = Capitulo 12",
                "capítulo iv = capítulo iv",
                "Parte IV = Parte 4",
                "Livro II = Livro 2",
                "Canto III = Canto 3",
                "Ato V = Ato 5",
                "Cena I = Cena 1",
                "Tomo CXL = Tomo 140",
                "Seção IX = Seção 9",
                "Volume X = Volume 10",
                "XII — O enterro = 12 — O enterro",
                "XII—O enterro = 12—O enterro",
                "IV. A volta = 4. A volta",
                "LI: Um encontro = 51: Um encontro",
                "IX - O fim = 9 - O fim",
                "LIVRO II — CAPÍTULO IV = Livro 2 — capítulo 4",
            ],
        )
        fun `turns Roman numerals into numbers`(
            text: String,
            expected: String,
        ) {
            assertThat(heading(text)).isEqualTo(expected)
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '=',
            quoteCharacter = '"',
            value = [
                "CAPÍTULO PRIMEIRO = Capítulo primeiro",
                "O ALIENISTA = O alienista",
                "DOM CASMURRO = DOM casmurro",
                "PARTE IV — A VOLTA = Parte 4 — A volta",
                "XII — O ENTERRO = 12 — O enterro",
                "GUARDA-CHUVA = Guarda-chuva",
                "MEMÓRIAS DE D'ÁGUA = Memórias DE D'água",
                "D. PEDRO VIII = D. pedro VIII",
                "A CIDADE E AS SERRAS = A cidade E AS serras",
            ],
        )
        fun `puts shouted words in sentence case`(
            text: String,
            expected: String,
        ) {
            assertThat(heading(text)).isEqualTo(expected)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "Dom Casmurro",
                "O Alienista",
                "Capítulo Primeiro",
                "ONU e USP",
                "D",
                "C",
                "L",
                "M",
                "Apêndice C",
                "Seção C",
                "Parte D",
                "Capítulo MCM",
                "Capítulo VX",
                "Livro IL",
                "MIX",
                "V. M. Fulano",
                "I. M. Fulano",
                "XIIa",
                "Capítulo 12",
            ],
        )
        fun `leaves headings alone when a rewrite could be wrong`(text: String) {
            assertThat(heading(text)).isEqualTo(text)
        }
    }

    @Nested
    inner class BodyText {
        @ParameterizedTest
        @CsvSource(
            delimiter = '=',
            value = [
                "No século XIX tudo mudou. = No século 19 tudo mudou.",
                "No SÉCULO XX houve guerras. = No SÉCULO 20 houve guerras.",
                "o seculo XXI = o seculo 21",
                "Leia o capítulo IV e a parte II. = Leia o capítulo 4 e a parte 2.",
                "(século V) = (século 5)",
                "no ato III, cena II = no ato 3, cena 2",
            ],
        )
        fun `turns numerals after section keywords into numbers`(
            text: String,
            expected: String,
        ) {
            assertThat(body(text)).isEqualTo(expected)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "D. Pedro I governou o Brasil.",
                "V. Exa. está enganado.",
                "Luís XIV foi rei da França.",
                "Pio X foi papa.",
                "Eu, Cláudio, e a letra I.",
                "XII",
                "Ele gritou: NÃO VOU!",
                "CAPÍTULO PRIMEIRO",
                "Um aparte II e um contrato IV.",
                "A parte C do formulário.",
                "Custa R$ 10,50 e pesa 3.5 kg.",
                "O Sr. Silva disse: «Vamos embora.» — Agora?",
                "Ele pensou.. e saiu.",
                "Fim. . . Começo.",
            ],
        )
        fun `leaves everything else untouched`(text: String) {
            assertThat(body(text)).isEqualTo(text)
        }
    }

    @Nested
    inner class Symbols {
        @ParameterizedTest
        @CsvSource(
            delimiter = '=',
            ignoreLeadingAndTrailingWhitespace = false,
            value = [
                "**Atenção**: leia tudo.=Atenção: leia tudo.",
                "texto * texto=texto texto",
                "um|dois=um dois",
                "palavra_com_sublinhado=palavra com sublinhado",
                "_O Guarani_ é um romance.=O Guarani é um romance.",
                "• Primeiro item=Primeiro item",
                "cerca de ~5 km=cerca de 5 km",
                "#1 da lista=1 da lista",
                "uma nota*.=uma nota.",
                "(*) Nota do editor=() Nota do editor",
                "Item 1 | Item 2=Item 1 Item 2",
            ],
        )
        fun `removes symbols that voices read badly`(
            text: String,
            expected: String,
        ) {
            assertThat(body(text)).isEqualTo(expected)
        }

        @Test
        fun `keeps line breaks around removed symbols`() {
            assertThat(body("linha\n• item\nfim *")).isEqualTo("linha\nitem\nfim")
        }

        @Test
        fun `may leave nothing to say`() {
            assertThat(body("* * *")).isEmpty()
            assertThat(heading("***")).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '=',
            value = [
                "Viva!!! = Viva!",
                "Sério??? = Sério?",
                "Como assim?!?! = Como assim?!",
                "O quê!? = O quê?!",
                "Basta‼ = Basta!",
                "Sério⁉ = Sério?!",
                "Ele pensou... e saiu. = Ele pensou… e saiu.",
                "Bem.... = Bem…",
                "Fim!!!... = Fim!…",
            ],
        )
        fun `collapses repeated marks and dots`(
            text: String,
            expected: String,
        ) {
            assertThat(body(text)).isEqualTo(expected)
        }
    }
}
