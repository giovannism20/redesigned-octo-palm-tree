package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.layout.PageSketch.Companion.LEFT
import com.giovannimartins.rreader.textkit.layout.PageSketch.Companion.RIGHT
import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LayoutBookBuilderTest {
    private val metadata = BookMetadata("Dom Casmurro", listOf("Machado de Assis"), language = "pt-BR")

    @Test
    fun `cleans a typeset book into chapters of speakable paragraphs`() {
        val result =
            LayoutBookBuilder().build(
                metadata,
                typesetBook(),
                listOf(OutlineEntry("Do título", 0), OutlineEntry("Do livro", 2)),
            )

        assertThat(result)
            .isEqualTo(
                LayoutResult.Success(
                    NormalizedBook(
                        metadata,
                        listOf(
                            Chapter.fromParagraphs(
                                "Do título",
                                listOf(
                                    "CAPÍTULO PRIMEIRO",
                                    "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem da " +
                                        "Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu. " +
                                        "Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros, e " +
                                        "acabou recitando-me versos.",
                                    "— Continue, disse eu, acordando.",
                                    "— Já acabei, murmurou ele, e fez-se um silêncio na conversa toda.",
                                ),
                            ),
                            Chapter.fromParagraphs(
                                "Do livro",
                                listOf(
                                    "CAPÍTULO SEGUNDO",
                                    "Depois, pensando no caso, achei que a noite tinha sido longa e que era preciso " +
                                        "dá-lhe um nome.",
                                    "Agora que expliquei o título, passo a escrever o livro.",
                                ),
                            ),
                        ),
                    ),
                ),
            )
    }

    @Test
    fun `reads pages in the order of their indexes`() {
        val pages = typesetBook()

        assertThat(LayoutBookBuilder().build(metadata, pages.reversed()))
            .isEqualTo(LayoutBookBuilder().build(metadata, pages))
    }

    @Test
    fun `reports a document without pages as having no readable text`() {
        assertThat(LayoutBookBuilder().build(metadata, emptyList()))
            .isEqualTo(LayoutResult.Failure(LayoutError.NoReadableText))
    }

    /** Four pages with running headers, page numbers on the outer side, hyphenation, enclisis and dialogue. */
    private fun typesetBook(): List<PageLayout> =
        listOf(
            page(0) {
                header("DOM CASMURRO", LEFT)
                centered("CAPÍTULO PRIMEIRO", size = 14f)
                gap()
                full("Uma noite destas, vindo da cidade para o", indent = true)
                full("Engenho Novo, encontrei no trem da Central um")
                full("rapaz aqui do bairro, que eu conheço de vista e")
                full("de chapéu. Cumprimentou-me, sentou-se ao pé de")
                full("mim, falou da lua e dos ministros, e acabou recitan-")
                last("do-me versos.")
                footer("1", LEFT)
            },
            page(1) {
                header("MACHADO DE ASSIS", RIGHT - 80f)
                full("— Continue, disse eu, acordando.")
                full("— Já acabei, murmurou ele, e fez-")
                last("se um silêncio na conversa toda.")
                footer("2", RIGHT - 10f)
            },
            page(2) {
                header("DOM CASMURRO", LEFT)
                centered("CAPÍTULO SEGUNDO", size = 14f)
                gap()
                full("Depois, pensando no caso, achei que a noi-", indent = true)
                full("te tinha sido longa e que era preciso dá-")
                last("lhe um nome.")
                footer("3", LEFT)
            },
            page(3) {
                header("MACHADO DE ASSIS", RIGHT - 80f)
                full("Agora que expliquei o título, passo a escrever", indent = true)
                last("o livro.")
                footer("4", RIGHT - 10f)
            },
        )
}
