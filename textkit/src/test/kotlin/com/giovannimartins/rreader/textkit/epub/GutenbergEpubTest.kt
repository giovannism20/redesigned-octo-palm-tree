package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** A book laid out as Project Gutenberg's ebookmaker builds its EPUBs, with synthetic text. */
class GutenbergEpubTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `reads a Gutenberg book without its header, credits and license`() {
        val fixture =
            EpubFixture().apply {
                document("wrap0000.xhtml", "<div><img src=\"capa.jpg\" alt=\"\"/></div>")
                document(
                    "h-0.xhtml",
                    HEADER + "<h1 id=\"t\">LIVRO DE TESTE</h1><h2>I</h2><p>Era uma vez.</p>",
                    tocTitle = "LIVRO DE TESTE",
                    tocHref = "h-0.xhtml#t",
                )
                document("h-1.xhtml", "<h2 id=\"ii\">II</h2><p>Fim.</p>", tocTitle = "II", tocHref = "h-1.xhtml#ii")
                document(
                    "h-2.xhtml",
                    FOOTER,
                    tocTitle = "THE FULL PROJECT GUTENBERG™ LICENSE",
                    tocHref = "h-2.xhtml#pg-footer-heading",
                )
            }

        val book = parseBook(fixture.writeTo(File(directory, "pg12345-images-3.epub")))

        assertThat(book.chapters)
            .containsExactly(
                Chapter("LIVRO DE TESTE", "LIVRO DE TESTE\n\nI\n\nEra uma vez."),
                Chapter("II", "II\n\nFim."),
            ).inOrder()
    }

    private companion object {
        const val HEADER =
            "<header class=\"pg-boilerplate pgheader\" id=\"pg-header\" lang=\"en\">" +
                "<h2 id=\"pg-header-heading\">The Project Gutenberg eBook of <span>Livro de Teste</span></h2>" +
                "<div>This eBook is for the use of anyone anywhere in the United States and most other parts of " +
                "the world at no cost and with almost no restrictions whatsoever.</div>" +
                "<div class=\"container\" id=\"pg-machine-header\"><p><strong>Title</strong>: Livro de Teste</p>" +
                "<div id=\"pg-header-authlist\"><p><strong>Author</strong>: Autora Fictícia</p></div>" +
                "<p><strong>Release date</strong>: January 1, 2020 [eBook #12345]</p>" +
                "<p><strong>Language</strong>: Portuguese</p>" +
                "<p><strong>Credits</strong>: Produced by volunteers</p></div>" +
                "<div id=\"pg-start-separator\"><span>*** START OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***" +
                "</span></div></header>" +
                "<p>Produced by volunteers and the Online Distributed Proofreading Team at https://www.pgdp.net</p>"

        const val FOOTER =
            "<footer class=\"pg-boilerplate pgheader\" id=\"pg-footer\" lang=\"en\">" +
                "<div id=\"pg-end-separator\"><span>*** END OF THE PROJECT GUTENBERG EBOOK LIVRO DE TESTE ***" +
                "</span></div>" +
                "<div>Updated editions will replace the previous one—the old editions will be renamed.</div>" +
                "<div id=\"project-gutenberg-license\">START: FULL LICENSE</div>" +
                "<h2 id=\"pg-footer-heading\">THE FULL PROJECT GUTENBERG™ LICENSE</h2>" +
                "<div class=\"agate\">PLEASE READ THIS BEFORE YOU DISTRIBUTE OR USE THIS WORK</div></footer>"
    }
}
