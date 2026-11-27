package com.giovannimartins.rreader.textkit.layout

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Paragraphs rebuilt from layout lines (see [ParagraphRules]). */
class ParagraphTest {
    @Test
    fun `starts a paragraph after a vertical gap`() {
        val spaced =
            page(0) {
                full("Linha um do primeiro parágrafo, sem recuo,")
                full("linha dois do primeiro parágrafo, e a última")
                full("linha também cheia termina com ponto final.")
                gap()
                full("Segundo parágrafo, separado só pelo espaço")
                full("entre eles e por nada mais na página toda.")
            }

        assertThat(paragraphsOf(spaced))
            .containsExactly(
                "Linha um do primeiro parágrafo, sem recuo, linha dois do primeiro parágrafo, " +
                    "e a última linha também cheia termina com ponto final.",
                "Segundo parágrafo, separado só pelo espaço entre eles e por nada mais na página toda.",
            ).inOrder()
    }

    @Test
    fun `starts a paragraph at a first-line indent`() {
        val indented =
            page(0) {
                full("Primeiro parágrafo com recuo na primeira linha,", indent = true)
                full("e a última linha dele também enche a coluna.")
                full("Segundo parágrafo começa recuado como manda a", indent = true)
                full("regra de composição dos livros impressos.")
            }

        assertThat(paragraphsOf(indented))
            .containsExactly(
                "Primeiro parágrafo com recuo na primeira linha, e a última linha dele também enche a coluna.",
                "Segundo parágrafo começa recuado como manda a regra de composição dos livros impressos.",
            ).inOrder()
    }

    @Test
    fun `starts a paragraph after a short line that ends a sentence, and only then`() {
        val blocks =
            page(0) {
                full("Um parágrafo sem recuo nem espaço entre eles")
                last("acaba aqui.")
                full("Ela disse que viria logo, mas o trem que a")
                last("trazia atrasou,")
                full("e ela chegou tarde para o jantar daquela noite")
                last("de festa.")
            }

        assertThat(paragraphsOf(blocks))
            .containsExactly(
                "Um parágrafo sem recuo nem espaço entre eles acaba aqui.",
                "Ela disse que viria logo, mas o trem que a trazia atrasou, e ela chegou tarde para o jantar daquela " +
                    "noite de festa.",
            ).inOrder()
    }

    @Test
    fun `tolerates the uneven line ends of ragged text`() {
        val ragged =
            page(0) {
                line("Num texto sem justificação as linhas acabam", right = 345f)
                line("em pontos diferentes da coluna, e esta frase", right = 341f)
                line("termina antes da margem por pouco.", right = 318f)
                line("A seguinte continua no mesmo parágrafo e só", right = 330f)
                line("acaba aqui.", right = 105f)
                line("Outro parágrafo começa na margem esquerda.", right = 300f)
                line("E termina.", right = 100f)
            }

        assertThat(paragraphsOf(ragged))
            .containsExactly(
                "Num texto sem justificação as linhas acabam em pontos diferentes da coluna, e esta frase termina " +
                    "antes da margem por pouco. A seguinte continua no mesmo parágrafo e só acaba aqui.",
                "Outro parágrafo começa na margem esquerda. E termina.",
            ).inOrder()
    }

    @Test
    fun `keeps dialogue lines as their own paragraphs but not a dash inside a sentence`() {
        val dialogue =
            page(0) {
                full("Ele olhou para ela e perguntou o que queria.")
                full("— Nada — respondeu ela, sem levantar os olhos.")
                full("– Nada mesmo? — insistiu ele, que não sabia")
                full("— e nunca saberia — o que ela sentia de fato.")
            }

        assertThat(paragraphsOf(dialogue))
            .containsExactly(
                "Ele olhou para ela e perguntou o que queria.",
                "— Nada — respondeu ela, sem levantar os olhos.",
                "– Nada mesmo? — insistiu ele, que não sabia — e nunca saberia — o que ela sentia de fato.",
            ).inOrder()
    }

    @Test
    fun `ends a paragraph after a centered line`() {
        val titled =
            page(0) {
                centered("O ENCONTRO")
                full("Era uma tarde quente quando eles se viram")
                last("pela primeira vez.")
            }

        assertThat(paragraphsOf(titled))
            .containsExactly("O ENCONTRO", "Era uma tarde quente quando eles se viram pela primeira vez.")
            .inOrder()
    }

    @Test
    fun `starts a paragraph where the font size changes`() {
        val quoted =
            page(0) {
                full("Antes da citação o texto segue normalmente e")
                full("termina a frase aqui sem linha curta nenhuma.")
                line("Uma citação em letra menor, como as epígrafes", size = 8f)
                line("costumam ser impressas nos livros antigos.", size = 8f)
                full("Depois dela o texto volta ao tamanho normal")
                full("e segue até o fim da página sem novidades.")
            }

        assertThat(paragraphsOf(quoted)).hasSize(3)
    }

    @Test
    fun `continues a paragraph across pages, measuring each page's own column`() {
        val recto =
            page(0) {
                full("O parágrafo começa nesta página e continua", indent = true)
                full("na seguinte sem interrupção alguma, porque")
                full("até o pé da página a frase não acabou, e a")
            }
        val verso =
            page(1) {
                line("frase só termina aqui, na página espelhada,", left = 70f, right = 370f)
                line("cuja margem esquerda fica mais à direita.", left = 70f, right = 370f)
                line("Novo parágrafo na segunda página e o fim.", left = 85f, right = 370f)
            }

        assertThat(paragraphsOf(recto, verso))
            .containsExactly(
                "O parágrafo começa nesta página e continua na seguinte sem interrupção alguma, porque até o pé da " +
                    "página a frase não acabou, e a frase só termina aqui, na página espelhada, cuja margem esquerda " +
                    "fica mais à direita.",
                "Novo parágrafo na segunda página e o fim.",
            ).inOrder()
    }

    @Test
    fun `measures a page with a single line against a nearby page`() {
        val full =
            page(0) {
                full("Um parágrafo que enche todas as linhas", indent = true)
                full("da coluna até a margem direita e termina")
                full("aqui mesmo, no fim da página, com ponto.")
            }
        val single = page(1) { last("Novo parágrafo curto.", indent = true) }

        assertThat(paragraphsOf(full, single, page(2) { story("Outro texto") }))
            .containsExactly(
                "Um parágrafo que enche todas as linhas da coluna até a margem direita e termina aqui mesmo, no fim " +
                    "da página, com ponto.",
                "Novo parágrafo curto.",
                storyText("Outro texto"),
            ).inOrder()
    }

    @Test
    fun `drops scene separators and ends the paragraph there`() {
        val scenes =
            page(0) {
                full("Fim de uma cena, que termina sem linha curta", indent = true)
                full("nem recuo, para que só o separador conte")
                centered("* * *")
                full("outra cena começa sem recuo depois do sinal")
                last("de separação.")
            }

        assertThat(paragraphsOf(scenes))
            .containsExactly(
                "Fim de uma cena, que termina sem linha curta nem recuo, para que só o separador conte",
                "outra cena começa sem recuo depois do sinal de separação.",
            ).inOrder()
    }

    @Test
    fun `joins a drop cap to the word it starts`() {
        val opening =
            page(0) {
                line("E", left = 50f, right = 70f, size = 30f)
                full("ra uma vez um reino distante onde vivia uma")
                last("princesa sem nome.")
            }

        assertThat(paragraphsOf(opening))
            .containsExactly("Era uma vez um reino distante onde vivia uma princesa sem nome.")
    }

    @Test
    fun `recognizes sentence ends before closing quotes and brackets`() {
        listOf("fim.", "fim!", "fim?", "fim…", "fim:", "fim;", "fim.”", "fim.»", "fim.)", "fim?' ").forEach {
            assertThat(ParagraphRules.endsSentence(it)).isTrue()
        }
        listOf("fim", "fim,", "fim —", "fim-", "").forEach { assertThat(ParagraphRules.endsSentence(it)).isFalse() }
    }
}
