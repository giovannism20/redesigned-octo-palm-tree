package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.layout.PageSketch.Companion.LEFT
import com.giovannimartins.rreader.textkit.layout.PageSketch.Companion.RIGHT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Running headers, running footers and page numbers (see [RunningLines]). */
class RunningLinesTest {
    @Test
    fun `removes a running header and the page numbers of every page`() {
        val pages =
            (0..2).map { index ->
                page(index) {
                    header("DOM CASMURRO")
                    story("Texto da página ${index + 1}")
                    footer("${index + 1}")
                }
            }

        assertThat(paragraphsOf(pages))
            .containsExactly(
                storyText("Texto da página 1"),
                storyText("Texto da página 2"),
                storyText("Texto da página 3"),
            ).inOrder()
    }

    @Test
    fun `removes alternating headers that carry the page number and numbers on the outer side`() {
        val pages =
            (0..3).map { index ->
                val printed = index + 12
                page(index) {
                    if (index % 2 == 0) header("$printed    DOM CASMURRO") else header("CAPÍTULO III    $printed", 150f)
                    story("Texto da página ${index + 1}")
                    if (index % 2 == 0) footer("$printed", LEFT) else footer("$printed", RIGHT - 10f)
                }
            }

        assertThat(paragraphsOf(pages))
            .containsExactly(
                storyText("Texto da página 1"),
                storyText("Texto da página 2"),
                storyText("Texto da página 3"),
                storyText("Texto da página 4"),
            ).inOrder()
    }

    @Test
    fun `removes a footer repeated on every other page and page numbers in the header band`() {
        val pages =
            (0..3).map { index ->
                page(index) {
                    header("— ${index + 1} —", if (index % 2 == 0) LEFT else RIGHT - 30f)
                    story("Corpo do texto da página ${index + 1}")
                    if (index % 2 == 1) footer("www.exemplo.com.br", LEFT)
                }
            }

        assertThat(paragraphsOf(pages))
            .containsExactly(
                storyText("Corpo do texto da página 1"),
                storyText("Corpo do texto da página 2"),
                storyText("Corpo do texto da página 3"),
                storyText("Corpo do texto da página 4"),
            ).inOrder()
    }

    @Test
    fun `keeps chapter headings printed at the top of distant pages, even when a number matches the page`() {
        val pages =
            (0..6).map { index ->
                page(index) {
                    when (index) {
                        0 -> header("Capítulo 1")
                        4 -> header("Capítulo 2")
                    }
                    story("Texto corrido da página ${index + 1}")
                    footer("${index + 1}")
                }
            }

        val chapters = buildBook(pages).chapters

        assertThat(chapters.map { it.title }).containsExactly("Capítulo 1", "Capítulo 2").inOrder()
        assertThat(chapters[0].text).startsWith("Capítulo 1\n\n${storyText("Texto corrido da página 1")}")
    }

    @Test
    fun `keeps numbers and repeated text that are not in the margins`() {
        val pages =
            (0..2).map { index ->
                page(index) {
                    header("DOM CASMURRO")
                    story("O ano em que tudo aconteceu")
                    centered("1888")
                    story("Depois disso")
                }
            }

        val paragraphs = paragraphsOf(pages)

        assertThat(paragraphs.count { it == "1888" }).isEqualTo(3)
        assertThat(paragraphs.count { it == storyText("Depois disso") }).isEqualTo(3)
    }

    @Test
    fun `keeps the margin text of a single page but still drops its page number`() {
        val single =
            page(0) {
                header("Um conto")
                story("Era uma vez uma história curta")
                footer("1")
            }

        assertThat(paragraphsOf(single)).containsExactly("Um conto", storyText("Era uma vez uma história curta"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["12", "— 12 —", "- 7 -", "[14]", "(3)", "12.", "· 9 ·", "xii", "XIV", "Página 15", "p. 3"])
    fun `recognizes page numbers`(text: String) {
        assertThat(RunningLines.isPageNumber(text)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["12 de março", "Capítulo 3", "IIII", "Xii", "iv.v", "12345", "Fim", "— Sim —"])
    fun `does not take other short lines for page numbers`(text: String) {
        assertThat(RunningLines.isPageNumber(text)).isFalse()
    }
}
