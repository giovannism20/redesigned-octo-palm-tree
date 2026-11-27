package com.giovannimartins.rreader.textkit.gutenberg

import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GutenbergBoilerplateTest {
    @Test
    fun `removes the header, the markers and the license that Gutenberg's ebookmaker adds`() {
        val book =
            listOf(
                chapter("LIVRO DE TESTE", *HEADER, START_LINE, "LIVRO DE TESTE", "Uma noite destas, vindo da cidade."),
                chapter("Do livro", "Agora que expliquei o título, passo a escrever o livro."),
                chapter("THE FULL PROJECT GUTENBERG™ LICENSE", END_LINE, *LICENSE),
            )

        val stripped = GutenbergBoilerplate.strip(book)

        assertThat(stripped)
            .containsExactly(
                chapter("LIVRO DE TESTE", "LIVRO DE TESTE", "Uma noite destas, vindo da cidade."),
                chapter("Do livro", "Agora que expliquei o título, passo a escrever o livro."),
            ).inOrder()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "*** START OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***",
            "*** START OF THIS PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***",
            "***START OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE***",
            "*** START OF THE PROJECT GUTENBERG E-BOOK LIVRO DE TESTE ***",
            "*** START OF THIS PROJECT GUTENBERG ETEXT LIVRO DE TESTE ***",
            "*** Start of the Project Gutenberg eBook Livro de Teste ***",
            "** START OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE",
        ],
    )
    fun `recognizes the start marker in the spellings Gutenberg used`(marker: String) {
        val stripped = GutenbergBoilerplate.strip(listOf(chapter(null, *HEADER, marker, "Era uma vez.")))

        assertThat(stripped).containsExactly(chapter(null, "Era uma vez."))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "*** END OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***",
            "*** END OF THIS PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***",
            "***END OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE***",
            "*** End of the Project Gutenberg E-Book Livro de Teste ***",
            "End of the Project Gutenberg EBook of Livro de Teste, by Autora Fictícia",
            "End of Project Gutenberg's Livro de Teste, by Autora Fictícia",
            "End of Project Gutenberg’s Livro de Teste, by Autora Fictícia",
            "END OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE",
        ],
    )
    fun `recognizes the end marker in the spellings Gutenberg used`(marker: String) {
        val stripped = GutenbergBoilerplate.strip(listOf(chapter(null, "Era uma vez.", "Fim.", marker, *LICENSE)))

        assertThat(stripped).containsExactly(chapter(null, "Era uma vez.", "Fim."))
    }

    @Test
    fun `removes the end-of-book line and the end marker that follows it in the next chapter`() {
        val book =
            listOf(
                chapter("I", *HEADER, START_LINE, "Era uma vez."),
                chapter("II", "Fim.", "End of Project Gutenberg's Livro de Teste, by Autora Fictícia"),
                chapter("THE FULL PROJECT GUTENBERG™ LICENSE", END_LINE, *LICENSE),
            )

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(chapter("I", "Era uma vez."), chapter("II", "Fim."))
            .inOrder()
    }

    @Test
    fun `keeps the book's text that shares a paragraph with a marker, as in an old preformatted header`() {
        val book =
            listOf(
                chapter(
                    null,
                    "The Project Gutenberg EBook of Livro de Teste This eBook is for the use of anyone anywhere " +
                        "*** START OF THIS PROJECT GUTENBERG EBOOK LIVRO DE TESTE *** CAPÍTULO PRIMEIRO",
                    "Era uma vez.",
                    "Fim. *** END OF THIS PROJECT GUTENBERG EBOOK LIVRO DE TESTE *** This file should be named 1.txt",
                    *LICENSE,
                ),
            )

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(chapter(null, "CAPÍTULO PRIMEIRO", "Era uma vez.", "Fim."))
    }

    @Test
    fun `removes production credits right after the start marker, not later in the book`() {
        val book =
            listOf(
                chapter(
                    null,
                    *HEADER,
                    START_LINE,
                    "Produced by Biblioteca Nacional Digital (http://bnd.bn.pt),",
                    "Rita Farinha and the Online Distributed Proofreading Team at https://www.pgdp.net",
                    "AMOR DE PERDIÇÃO",
                    "Produced by a careful hand, the letter reached the house at night.",
                ),
            )

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(
                chapter(null, "AMOR DE PERDIÇÃO", "Produced by a careful hand, the letter reached the house at night."),
            )
    }

    @Test
    fun `removes credits that share the start marker's paragraph`() {
        val book =
            listOf(
                chapter(null, "*** START OF THE PROJECT GUTENBERG EBOOK X *** E-text prepared by volunteers", "Texto."),
            )

        assertThat(GutenbergBoilerplate.strip(book)).containsExactly(chapter(null, "Texto."))
    }

    @Test
    fun `returns a book without markers unchanged`() {
        val book =
            listOf(
                chapter("I", "Era uma vez um livro que citava o Project Gutenberg no meio do texto."),
                chapter("II", "*** START: FULL LICENSE ***", "THE FULL PROJECT GUTENBERG LICENSE"),
            )

        assertThat(GutenbergBoilerplate.strip(book)).isSameInstanceAs(book)
    }

    @Test
    fun `removes only the header when the book has no end marker`() {
        val book = listOf(chapter(null, *HEADER, START_LINE, "Era uma vez."), chapter("II", "Fim."))

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(chapter(null, "Era uma vez."), chapter("II", "Fim."))
            .inOrder()
    }

    @Test
    fun `removes only the license when the book has no start marker`() {
        val book = listOf(chapter("I", "Era uma vez."), chapter("II", "Fim.", END_LINE, *LICENSE))

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(chapter("I", "Era uma vez."), chapter("II", "Fim."))
            .inOrder()
    }

    @Test
    fun `looks for the start marker only at the beginning of the book`() {
        val paragraphs = List(GutenbergBoilerplate.HEADER_PARAGRAPHS) { "Parágrafo ${it + 1}." }
        val book = listOf(chapter(null, *paragraphs.toTypedArray(), START_LINE, "Continua."))

        assertThat(GutenbergBoilerplate.strip(book)).isSameInstanceAs(book)
    }

    @Test
    fun `looks for the end marker only after the start marker`() {
        val book =
            listOf(chapter(null, "End of the Project Gutenberg header, an odd line", START_LINE, "Era uma vez."))

        assertThat(GutenbergBoilerplate.strip(book)).containsExactly(chapter(null, "Era uma vez."))
    }

    @Test
    fun `leaves a book unchanged when its markers would leave no text`() {
        val book = listOf(chapter(null, *HEADER, START_LINE), chapter("License", END_LINE, *LICENSE))

        assertThat(GutenbergBoilerplate.strip(book)).isSameInstanceAs(book)
    }

    @Test
    fun `drops a chapter title taken from Gutenberg's header and keeps the others`() {
        val book =
            listOf(
                chapter("The Project Gutenberg eBook of Livro de Teste", *HEADER, START_LINE, "Era uma vez."),
                chapter("Capítulo II", "Fim.", END_LINE, *LICENSE),
            )

        assertThat(GutenbergBoilerplate.strip(book))
            .containsExactly(chapter(null, "Era uma vez."), chapter("Capítulo II", "Fim."))
            .inOrder()
    }

    @Test
    fun `tells which chapter each remaining chapter comes from`() {
        val book =
            listOf(
                chapter("Rosto", *HEADER, START_LINE),
                chapter("I", "Era uma vez."),
                chapter("II", "Fim.", END_LINE),
                chapter("Licença", *LICENSE),
            )

        assertThat(GutenbergBoilerplate.stripIndexed(book))
            .containsExactly(IndexedValue(1, chapter("I", "Era uma vez.")), IndexedValue(2, chapter("II", "Fim.")))
            .inOrder()
        assertThat(GutenbergBoilerplate.stripIndexed(listOf(chapter("I", "Sem marcas.")))).isNull()
    }

    @Test
    fun `keeps the book's own paragraphs exactly as they were`() {
        val dialogue = "—Continue, disse eu accordando. «Dom Casmurro, domingo vou jantar com você.»"
        val book = listOf(chapter(null, *HEADER, START_LINE, dialogue, "Não consultes diccionarios.", END_LINE))

        assertThat(GutenbergBoilerplate.strip(book).single().text)
            .isEqualTo("$dialogue${Chapter.PARAGRAPH_SEPARATOR}Não consultes diccionarios.")
    }

    private fun chapter(
        title: String?,
        vararg paragraphs: String,
    ) = Chapter.fromParagraphs(title, paragraphs.toList())

    private companion object {
        val HEADER =
            arrayOf(
                "The Project Gutenberg eBook of Livro de Teste",
                "This eBook is for the use of anyone anywhere in the United States and most other parts of the world.",
                "Title: Livro de Teste",
                "Author: Autora Fictícia",
                "Release date: January 1, 2020 [eBook #12345]",
                "Language: Portuguese",
                "Credits: Produced by volunteers",
            )
        const val START_LINE = "*** START OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***"
        const val END_LINE = "*** END OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***"
        val LICENSE =
            arrayOf(
                "Updated editions will replace the previous one—the old editions will be renamed.",
                "START: FULL LICENSE",
                "THE FULL PROJECT GUTENBERG™ LICENSE",
                "PLEASE READ THIS BEFORE YOU DISTRIBUTE OR USE THIS WORK",
            )
    }
}
