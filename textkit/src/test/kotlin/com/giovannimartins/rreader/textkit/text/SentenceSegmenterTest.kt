package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Locale

class SentenceSegmenterTest {
    private val segmenter = SentenceSegmenter()

    /** Splits [text], checks the range contract and returns the sentences as strings. */
    private fun sentences(
        text: String,
        using: SentenceSegmenter = segmenter,
    ): List<String> {
        val ranges = using.split(text)
        assertWellFormed(text, ranges, using.maxSentenceLength)
        return text.slices(ranges)
    }

    @Test
    fun `splits plain sentences`() {
        assertThat(sentences("Era uma vez um rei. Ele tinha três filhas! Qual era a mais nova?"))
            .containsExactly("Era uma vez um rei.", "Ele tinha três filhas!", "Qual era a mais nova?")
            .inOrder()
    }

    @Test
    fun `returns ranges into the original text`() {
        val text = "  Primeira frase.  Segunda frase.\n\nTerceira.  "

        assertThat(segmenter.split(text))
            .containsExactly(TextRange(2, 17), TextRange(19, 33), TextRange(35, 44))
            .inOrder()
    }

    @Test
    fun `returns nothing for blank text`() {
        assertThat(segmenter.split("")).isEmpty()
        assertThat(segmenter.split("  \n\n   ")).isEmpty()
    }

    @Test
    fun `keeps text without letters as one sentence`() {
        assertThat(sentences("* * *")).containsExactly("* * *")
        assertThat(sentences("...")).containsExactly("...")
    }

    @Test
    fun `splits public-domain prose by Machado de Assis`() {
        val text =
            "As crônicas da vila de Itaguaí dizem que em tempos remotos vivera ali um certo médico, o Dr. Simão " +
                "Bacamarte, filho da nobreza da terra e o maior dos médicos do Brasil, de Portugal e das Espanhas. " +
                "Estudara em Coimbra e Pádua.\n\n" +
                "— A ciência, disse ele a Sua Majestade, é o meu emprego único; Itaguaí é o meu universo.\n\n" +
                "Aos quarenta anos casou com D. Evarista da Costa e Mascarenhas, senhora de vinte e cinco anos, " +
                "viúva de um juiz de fora, e não bonita nem simpática. Era o que ele queria."

        assertThat(sentences(text))
            .containsExactly(
                "As crônicas da vila de Itaguaí dizem que em tempos remotos vivera ali um certo médico, o Dr. " +
                    "Simão Bacamarte, filho da nobreza da terra e o maior dos médicos do Brasil, de Portugal e " +
                    "das Espanhas.",
                "Estudara em Coimbra e Pádua.",
                "— A ciência, disse ele a Sua Majestade, é o meu emprego único; Itaguaí é o meu universo.",
                "Aos quarenta anos casou com D. Evarista da Costa e Mascarenhas, senhora de vinte e cinco anos, " +
                    "viúva de um juiz de fora, e não bonita nem simpática.",
                "Era o que ele queria.",
            ).inOrder()
    }

    @Test
    fun `rejects a limit too small to cut safely`() {
        assertThrows<IllegalArgumentException> { SentenceSegmenter(maxSentenceLength = 1) }
    }

    @Nested
    inner class Abbreviations {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "O Sr. Silva chegou cedo.",
                "A Sra. Costa e a Srta. Lima chegaram juntas.",
                "O Dr. Bacamarte e a Dra. Evarista conversaram.",
                "O SR. SILVA CHEGOU CEDO.",
                "O Exmo. Sr. Presidente e a Exma. Sra. Ministra discursaram.",
                "O Ilmo. Sr. Diretor assinou a carta.",
                "O Prof. Almeida e a Profa. Souza deram aula.",
                "Rezou para Sto. Antônio e para Sta. Luzia.",
                "O Pe. Antônio e o Fr. Luís rezaram.",
                "Leia a pág. 12 e as págs. 30 a 40.",
                "Veja o cap. 3 e os caps. 4 e 5.",
                "Está no vol. 2 da ed. Garnier.",
                "Por ex. Machado de Assis escreveu contos.",
                "Mora no nº. 25 da rua.",
                "Conforme o art. 5 da Constituição.",
                "Era o séc. XIX no Brasil.",
                "Mora na Av. Paulista desde cedo.",
                "Trabalha na Cia. Ltda. de tecidos.",
                "Veja op. cit. na nota.",
            ],
        )
        fun `does not split after abbreviations that precede a name or a number`(sentence: String) {
            assertThat(sentences("$sentence Depois voltou para casa."))
                .containsExactly(sentence, "Depois voltou para casa.")
                .inOrder()
        }

        @Test
        fun `splits after etc when an uppercase word follows`() {
            assertThat(sentences("Comprou maçãs, peras, etc. Depois voltou para casa."))
                .containsExactly("Comprou maçãs, peras, etc.", "Depois voltou para casa.")
                .inOrder()
            assertThat(sentences("Visitou Lisboa, Porto, etc. Coimbra ficou para depois."))
                .containsExactly("Visitou Lisboa, Porto, etc.", "Coimbra ficou para depois.")
                .inOrder()
        }

        @Test
        fun `keeps etc inside the sentence when it goes on`() {
            assertThat(sentences("Trouxe livros, cadernos etc. para a escola."))
                .containsExactly("Trouxe livros, cadernos etc. para a escola.")
            assertThat(sentences("Trouxe livros, cadernos, etc., e voltou."))
                .containsExactly("Trouxe livros, cadernos, etc., e voltou.")
            assertThat(sentences("Trouxe livros (cadernos etc.) e voltou."))
                .containsExactly("Trouxe livros (cadernos etc.) e voltou.")
        }

        @Test
        fun `ends the sentence at etc before a paragraph or a line break`() {
            assertThat(sentences("Comprou frutas, verduras etc.\n\nDepois voltou."))
                .containsExactly("Comprou frutas, verduras etc.", "Depois voltou.")
                .inOrder()
            assertThat(sentences("Comprou frutas, verduras etc.\ndepois voltou."))
                .containsExactly("Comprou frutas, verduras etc.", "depois voltou.")
                .inOrder()
        }

        @Test
        fun `treats company and name suffixes like etc`() {
            assertThat(sentences("Trabalha na Silva & Filhos Ltda. Ele é gerente."))
                .containsExactly("Trabalha na Silva & Filhos Ltda.", "Ele é gerente.")
                .inOrder()
            assertThat(sentences("O livro é de José da Silva Jr. Ele escreveu muito."))
                .containsExactly("O livro é de José da Silva Jr.", "Ele escreveu muito.")
                .inOrder()
            assertThat(sentences("Chegou José da Silva Jr. e sentou-se."))
                .containsExactly("Chegou José da Silva Jr. e sentou-se.")
            assertThat(sentences("Trabalha na Fulano S.A. Ela exporta café."))
                .containsExactly("Trabalha na Fulano S.A.", "Ela exporta café.")
                .inOrder()
        }

        @Test
        fun `splits after eras only when an uppercase word follows`() {
            assertThat(sentences("Nasceu em 63 a.C. e morreu em 14 d.C. Foi o primeiro imperador."))
                .containsExactly("Nasceu em 63 a.C. e morreu em 14 d.C.", "Foi o primeiro imperador.")
                .inOrder()
        }

        @Test
        fun `keeps forms of address that end in an abbreviation`() {
            assertThat(sentences("V. Exa. está enganado. O Exmo. Sr. Presidente chegou."))
                .containsExactly("V. Exa. está enganado.", "O Exmo. Sr. Presidente chegou.")
                .inOrder()
            assertThat(sentences("Obrigado, V. Exa. Até amanhã."))
                .containsExactly("Obrigado, V. Exa.", "Até amanhã.")
                .inOrder()
        }

        @Test
        fun `does not mistake the end of a hyphenated word for an abbreviation`() {
            assertThat(sentences("Encontrei-o. Maria ficou feliz."))
                .containsExactly("Encontrei-o.", "Maria ficou feliz.")
                .inOrder()
            assertThat(sentences("Peguei-a. Maria gostou do presente."))
                .containsExactly("Peguei-a.", "Maria gostou do presente.")
                .inOrder()
        }

        @Test
        fun `applies Portuguese abbreviations only to Portuguese text`() {
            val text = "I love art. It is beautiful."

            assertThat(sentences(text)).containsExactly(text)
            assertThat(sentences(text, SentenceSegmenter(Locale.ENGLISH)))
                .containsExactly("I love art.", "It is beautiful.")
                .inOrder()
            assertThat(sentences("Ele mora na Av. Brasil.", SentenceSegmenter(Locale.forLanguageTag("pt-PT"))))
                .containsExactly("Ele mora na Av. Brasil.")
        }
    }

    @Nested
    inner class InitialsAndNumbers {
        @Test
        fun `keeps initials with the name`() {
            assertThat(sentences("J. M. Machado de Assis escreveu Dom Casmurro. Ele nasceu no Rio de Janeiro."))
                .containsExactly("J. M. Machado de Assis escreveu Dom Casmurro.", "Ele nasceu no Rio de Janeiro.")
                .inOrder()
            assertThat(sentences("J.M. Machado de Assis nasceu em 1839. D. Pedro II reinava."))
                .containsExactly("J.M. Machado de Assis nasceu em 1839.", "D. Pedro II reinava.")
                .inOrder()
            assertThat(sentences("Morava em S. Paulo, na R. Direita, perto de S. João."))
                .containsExactly("Morava em S. Paulo, na R. Direita, perto de S. João.")
        }

        @Test
        fun `splits after a single letter when a typical sentence opener follows`() {
            assertThat(sentences("O imperador era D. Pedro I. Ele governou o Brasil."))
                .containsExactly("O imperador era D. Pedro I.", "Ele governou o Brasil.")
                .inOrder()
            assertThat(sentences("Tomou a vitamina C. Depois dormiu."))
                .containsExactly("Tomou a vitamina C.", "Depois dormiu.")
                .inOrder()
            assertThat(sentences("Assinado: J. A. Silva."))
                .containsExactly("Assinado: J. A. Silva.")
        }

        @Test
        fun `splits after a Roman numeral that ends a sentence`() {
            assertThat(sentences("Reinava D. João VI. Ele fugiu para o Brasil."))
                .containsExactly("Reinava D. João VI.", "Ele fugiu para o Brasil.")
                .inOrder()
            assertThat(sentences("Era o séc. XIX. Tudo mudou."))
                .containsExactly("Era o séc. XIX.", "Tudo mudou.")
                .inOrder()
        }

        @Test
        fun `never splits decimals, thousands or prices`() {
            assertThat(sentences("O frasco tem 3.5 litros. São 1.000 páginas. Custa R$ 10,50 hoje."))
                .containsExactly("O frasco tem 3.5 litros.", "São 1.000 páginas.", "Custa R$ 10,50 hoje.")
                .inOrder()
            assertThat(sentences("Custou R$ 1.250,00. Era caro."))
                .containsExactly("Custou R$ 1.250,00.", "Era caro.")
                .inOrder()
            assertThat(sentences("O preço é 3.5. Isso é tudo."))
                .containsExactly("O preço é 3.5.", "Isso é tudo.")
                .inOrder()
        }

        @Test
        fun `keeps references to pages and numbers whole`() {
            assertThat(sentences("Ver pág. 12 do livro. Veja também p. 13. O cap. 3 é bom."))
                .containsExactly("Ver pág. 12 do livro.", "Veja também p. 13.", "O cap. 3 é bom.")
                .inOrder()
            assertThat(sentences("Ele tinha 2 m. de altura. Isso é alto."))
                .containsExactly("Ele tinha 2 m. de altura.", "Isso é alto.")
                .inOrder()
        }

        @Test
        fun `keeps a list marker with its item`() {
            assertThat(sentences("1. Primeiro item. 2. Segundo item."))
                .containsExactly("1. Primeiro item.", "2. Segundo item.")
                .inOrder()
            assertThat(sentences("IV. O retorno do herói."))
                .containsExactly("IV. O retorno do herói.")
            assertThat(sentences("Ele nasceu em 1839. 1840 foi um ano difícil."))
                .containsExactly("Ele nasceu em 1839.", "1840 foi um ano difícil.")
                .inOrder()
        }
    }

    @Nested
    inner class PunctuationAndDialogue {
        @Test
        fun `continues after an ellipsis followed by a lowercase word`() {
            assertThat(sentences("Ele pensou... e depois saiu. Ficou tudo em silêncio... Ninguém falou."))
                .containsExactly("Ele pensou... e depois saiu.", "Ficou tudo em silêncio...", "Ninguém falou.")
                .inOrder()
            assertThat(sentences("Olá… tudo bem? Era uma vez… Um rei muito velho vivia ali."))
                .containsExactly("Olá… tudo bem?", "Era uma vez…", "Um rei muito velho vivia ali.")
                .inOrder()
            assertThat(sentences("Ele pensou . . . e saiu. . . . Ninguém viu."))
                .containsExactly("Ele pensou . . . e saiu. . . .", "Ninguém viu.")
                .inOrder()
        }

        @Test
        fun `continues after a question or exclamation followed by a lowercase word`() {
            assertThat(sentences("Quem? perguntou ele. Ninguém sabe!"))
                .containsExactly("Quem? perguntou ele.", "Ninguém sabe!")
                .inOrder()
            assertThat(sentences("Ah! não diga isso."))
                .containsExactly("Ah! não diga isso.")
        }

        @Test
        fun `starts a new sentence at each line of dialogue but not at the narrator's tag`() {
            assertThat(sentences("— Sim, senhor — respondeu ele. — Vamos?"))
                .containsExactly("— Sim, senhor — respondeu ele.", "— Vamos?")
                .inOrder()
            assertThat(sentences("— Vamos? — perguntou ele. — Sim!"))
                .containsExactly("— Vamos? — perguntou ele.", "— Sim!")
                .inOrder()
            assertThat(sentences("— Saia daqui! — gritou o homem."))
                .containsExactly("— Saia daqui! — gritou o homem.")
            assertThat(sentences("— Não sei — disse ela. — Talvez amanhã. — E saiu."))
                .containsExactly("— Não sei — disse ela.", "— Talvez amanhã.", "— E saiu.")
                .inOrder()
        }

        @Test
        fun `handles dialogue typed with hyphens or en dashes`() {
            assertThat(sentences("- Vamos? - perguntou ele. - Sim."))
                .containsExactly("- Vamos? - perguntou ele.", "- Sim.")
                .inOrder()
            assertThat(sentences("– Vamos? – perguntou ele. – Sim."))
                .containsExactly("– Vamos? – perguntou ele.", "– Sim.")
                .inOrder()
        }

        @Test
        fun `keeps closing quotes and brackets with their sentence`() {
            assertThat(sentences("Ele disse: «Vamos embora.» Ela não respondeu."))
                .containsExactly("Ele disse: «Vamos embora.»", "Ela não respondeu.")
                .inOrder()
            assertThat(sentences("Ele disse: “Vamos embora.” Ela não respondeu."))
                .containsExactly("Ele disse: “Vamos embora.”", "Ela não respondeu.")
                .inOrder()
            assertThat(sentences("Ele disse: \"Sim.\" Ela disse: \"Não.\""))
                .containsExactly("Ele disse: \"Sim.\"", "Ela disse: \"Não.\"")
                .inOrder()
            assertThat(sentences("Ele disse: « Vamos embora. » Ela não respondeu."))
                .containsExactly("Ele disse: « Vamos embora. »", "Ela não respondeu.")
                .inOrder()
            assertThat(sentences("Ele saiu (e voltou.) Depois dormiu."))
                .containsExactly("Ele saiu (e voltou.)", "Depois dormiu.")
                .inOrder()
        }

        @Test
        fun `starts a sentence at an opening quote or bracket`() {
            assertThat(sentences("Ele disse. «Vamos embora.» Ela não respondeu."))
                .containsExactly("Ele disse.", "«Vamos embora.»", "Ela não respondeu.")
                .inOrder()
            assertThat(sentences("Ele saiu. (Depois voltou.) Dormiu."))
                .containsExactly("Ele saiu.", "(Depois voltou.)", "Dormiu.")
                .inOrder()
            assertThat(sentences("Ele saiu. «Volto já», disse."))
                .containsExactly("Ele saiu.", "«Volto já», disse.")
                .inOrder()
        }

        @Test
        fun `continues when a quotation is followed by a lowercase tag`() {
            assertThat(sentences("«Vamos embora!» disse ela. “Agora?” perguntou ele."))
                .containsExactly("«Vamos embora!» disse ela.", "“Agora?” perguntou ele.")
                .inOrder()
        }

        @Test
        fun `keeps repeated and mixed marks together`() {
            assertThat(sentences("Viva!!! Que bom?! Sim.")).containsExactly("Viva!!!", "Que bom?!", "Sim.").inOrder()
        }

        @Test
        fun `ignores spurious break iterator boundaries next to a period`() {
            // The JVM's iterator splits this line into "»Ver pág", "." and " '".
            assertThat(sentences("»Ver pág. '")).containsExactly("»Ver pág. '")
        }

        @Test
        fun `uses the break iterator for terminators the rules do not know`() {
            assertThat(sentences("今日は晴れです。明日は雨です。")).containsExactly("今日は晴れです。", "明日は雨です。").inOrder()
            assertThat(
                sentences("यह एक वाक्य है। यह दूसरा है।"),
            ).containsExactly("यह एक वाक्य है।", "यह दूसरा है।").inOrder()
        }
    }

    @Nested
    inner class Boundaries {
        @Test
        fun `never crosses a paragraph`() {
            assertThat(sentences("Primeiro parágrafo sem ponto\n\nSegundo parágrafo."))
                .containsExactly("Primeiro parágrafo sem ponto", "Segundo parágrafo.")
                .inOrder()
            assertThat(sentences("Mora na Av.\n\nPaulista é longe."))
                .containsExactly("Mora na Av.", "Paulista é longe.")
                .inOrder()
            assertThat(sentences("— Vamos?\n\n— perguntou ele."))
                .containsExactly("— Vamos?", "— perguntou ele.")
                .inOrder()
        }

        @Test
        fun `ends a sentence at every line break`() {
            assertThat(sentences("Minha terra tem palmeiras,\nOnde canta o Sabiá;\r\nAs aves, que aqui gorjeiam,"))
                .containsExactly("Minha terra tem palmeiras,", "Onde canta o Sabiá;", "As aves, que aqui gorjeiam,")
                .inOrder()
        }

        @Test
        fun `treats non-breaking spaces as spaces`() {
            assertThat(sentences("Fim. Outro começo. "))
                .containsExactly("Fim.", "Outro começo.")
                .inOrder()
        }
    }

    @Nested
    inner class LongSentences {
        private val short = SentenceSegmenter(maxSentenceLength = 40)

        @Test
        fun `prefers a semicolon to a comma`() {
            val text = "Ele andou pela praia, olhou o mar; depois voltou para casa e dormiu cedo."

            assertThat(sentences(text, short))
                .containsExactly("Ele andou pela praia, olhou o mar;", "depois voltou para casa e dormiu cedo.")
                .inOrder()
        }

        @Test
        fun `cuts at a colon, then a spaced dash, then a comma`() {
            assertThat(sentences("Ele trouxe tudo o que tinha: livros, cadernos e lápis.", short))
                .containsExactly("Ele trouxe tudo o que tinha:", "livros, cadernos e lápis.")
                .inOrder()
            assertThat(sentences("Ele pensou muito no assunto — e então decidiu partir.", short))
                .containsExactly("Ele pensou muito no assunto", "— e então decidiu partir.")
                .inOrder()
            assertThat(sentences("Ele andou pela praia deserta, olhando o mar e as gaivotas.", short))
                .containsExactly("Ele andou pela praia deserta,", "olhando o mar e as gaivotas.")
                .inOrder()
        }

        @Test
        fun `ignores breaks that would leave a tiny piece`() {
            val text = "Sim; ele andou pela praia deserta olhando o mar e as gaivotas ao longe."

            assertThat(sentences(text, short))
                .containsExactly("Sim; ele andou pela praia deserta", "olhando o mar e as gaivotas ao longe.")
                .inOrder()
        }

        @Test
        fun `cuts at the last space when there is no punctuation`() {
            val text = "palavra ".repeat(12).trim()

            assertThat(sentences(text, short))
                .containsExactly("palavra ".repeat(5).trim(), "palavra ".repeat(5).trim(), "palavra palavra")
                .inOrder()
        }

        @Test
        fun `does not cut inside numbers`() {
            val text = "O preço total foi de R$ 1.250,50 mais 10,25 de taxa e 3:30 de espera na fila."

            assertThat(sentences(text, short).joinToString("|")).doesNotContainMatch("\\d[|]\\d")
        }

        @Test
        fun `cuts a huge word at the limit without splitting a surrogate pair`() {
            val word = "a".repeat(39) + "😀" + "b".repeat(30)

            val pieces = sentences(word, short)

            assertThat(pieces).containsExactly("a".repeat(39), "😀" + "b".repeat(30)).inOrder()
        }

        @Test
        fun `cuts long sentences with the default limit`() {
            val clause = "o vento soprava forte sobre as casas da vila"
            val text = List(10) { clause }.joinToString(", ") + "."

            val pieces = sentences(text)

            assertThat(pieces.size).isAtLeast(2)
            assertThat(pieces.all { it.length <= SentenceSegmenter.DEFAULT_MAX_SENTENCE_LENGTH }).isTrue()
            assertThat(pieces.dropLast(1).all { it.endsWith(",") }).isTrue()
        }
    }
}
