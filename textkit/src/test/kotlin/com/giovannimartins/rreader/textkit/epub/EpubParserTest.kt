package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EpubParserTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `parses an EPUB 3 book with chapter titles from the navigation document`() {
        val fixture =
            EpubFixture().apply {
                document("capa.xhtml", "<div><img src=\"capa.jpg\" alt=\"Capa\"/></div>")
                document("c1.xhtml", "<h1>Capítulo I</h1><p>Era uma vez.</p>", tocTitle = "Capítulo 1")
                document("c2.xhtml", "<h1>Capítulo II</h1><p>Fim.</p>", "Capítulo 2", tocHref = "c2.xhtml#ii")
            }

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.metadata).isEqualTo(BookMetadata("Livro de Teste", listOf("Autora Fictícia"), "pt-BR"))
        assertThat(book.chapters)
            .containsExactly(
                Chapter("Capítulo 1", "Capítulo I\n\nEra uma vez."),
                Chapter("Capítulo 2", "Capítulo II\n\nFim."),
            ).inOrder()
        assertThat(book.cover).isNull()
    }

    @Test
    fun `parses an EPUB 2 book with titles from the NCX, nested entries included`() {
        val fixture =
            EpubFixture(epub3 = false).apply {
                document("parte1.xhtml", "<h1>Parte I</h1>")
                document("c1.xhtml", "<p>Primeiro capítulo.</p>")
                document("c2.xhtml", "<p>Segundo capítulo.</p>")
            }
        val nested = navPoint("Parte I", "parte1.xhtml", navPoint("Um", "c1.xhtml#c1"), navPoint("Dois", "c2.xhtml"))
        fixture.entry(fixture.pathOf(EpubFixture.NCX_FILE), ncx(nested))

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters)
            .containsExactly(
                Chapter("Parte I", "Parte I"),
                Chapter("Um", "Primeiro capítulo."),
                Chapter("Dois", "Segundo capítulo."),
            ).inOrder()
    }

    @Test
    fun `flattens nested lists of the navigation document in document order`() {
        val fixture =
            EpubFixture().apply {
                document("parte1.xhtml", "<h1>Parte I</h1>")
                document("c1.xhtml", "<p>Primeiro capítulo.</p>")
                document("c2.xhtml", "<p>Segundo capítulo.</p>")
            }
        val toc =
            "<nav epub:type=\"toc\"><ol><li><a href=\"parte1.xhtml\">Parte <em>I</em></a><ol>" +
                "<li><a href=\"c1.xhtml\">Um</a></li><li><span>Sem link</span><ol><li><a href=\"c2.xhtml#x\">Dois</a>" +
                "</li></ol></li></ol></li></ol></nav><nav epub:type=\"landmarks\"><a href=\"c2.xhtml\">Errado</a></nav>"
        fixture.entry(fixture.pathOf(EpubFixture.NAV_FILE), xhtml(toc))

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters.map { it.title }).containsExactly("Parte I", "Um", "Dois").inOrder()
    }

    @Test
    fun `joins documents the table of contents does not point to with the chapter before them`() {
        val fixture =
            EpubFixture().apply {
                document("c1a.xhtml", "<h1>Um</h1><p>Começo do capítulo.</p>", tocTitle = "Capítulo 1")
                document("c1b.xhtml", "<p>Meio do capítulo.</p>")
                document("c1c.xhtml", "<p>Fim do capítulo.</p>")
                document("c2.xhtml", "<p>Outro capítulo.</p>", tocTitle = "Capítulo 2")
            }

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters)
            .containsExactly(
                Chapter("Capítulo 1", "Um\n\nComeço do capítulo.\n\nMeio do capítulo.\n\nFim do capítulo."),
                Chapter("Capítulo 2", "Outro capítulo."),
            ).inOrder()
    }

    @Test
    fun `keeps front matter before the first table-of-contents target as a chapter titled by its heading`() {
        val fixture =
            EpubFixture().apply {
                document("capa.xhtml", "<img src=\"capa.jpg\" alt=\"Capa\"/>")
                document("rosto.xhtml", "<h1>Livro de Teste</h1><p>Autora Fictícia</p>")
                document("dedicatoria.xhtml", "<p>Para quem escuta.</p>")
                document("c1.xhtml", "<p>Texto.</p>", tocTitle = "Capítulo 1")
            }

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters)
            .containsExactly(
                Chapter("Livro de Teste", "Livro de Teste\n\nAutora Fictícia\n\nPara quem escuta."),
                Chapter("Capítulo 1", "Texto."),
            ).inOrder()
    }

    @Test
    fun `makes every document a chapter titled by its first heading when the table of contents points nowhere`() {
        val fixture =
            EpubFixture().apply {
                document("a.xhtml", "<h2>Abertura</h2><p>Um.</p>", tocTitle = "Perdido", tocHref = "nao-existe.xhtml")
                document("b.xhtml", "<p>Sem título.</p>")
                document("c.xhtml", "<div><img src=\"foto.jpg\" alt=\"\"/></div>")
                document("d.xhtml", "<h3>Final</h3><p>Dois.</p>")
            }

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters)
            .containsExactly(
                Chapter("Abertura", "Abertura\n\nUm."),
                Chapter(null, "Sem título."),
                Chapter("Final", "Final\n\nDois."),
            ).inOrder()
    }

    @Test
    fun `uses the NCX when the navigation document points to no spine document`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Um.</p>", tocTitle = "Errado", tocHref = "outro.xhtml")
                document("c2.xhtml", "<p>Dois.</p>")
                extraManifest += manifestItem("ncx", "toc.ncx", "application/x-dtbncx+xml")
            }
        fixture.entry(fixture.pathOf("toc.ncx"), ncx(navPoint("Primeiro", "c1.xhtml"), navPoint("Segundo", "c2.xhtml")))

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters.map { it.title }).containsExactly("Primeiro", "Segundo").inOrder()
    }

    @Test
    fun `prefers the navigation document over the NCX`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Um.</p>", tocTitle = "Do sumário")
                extraManifest += manifestItem("ncx", "toc.ncx", "application/x-dtbncx+xml")
            }
        fixture.entry(fixture.pathOf("toc.ncx"), ncx(navPoint("Do NCX", "c1.xhtml")))

        assertThat(parseBook(fixture.writeTo(file())).chapters.single().title).isEqualTo("Do sumário")
    }

    @Test
    fun `skips non-linear spine items and documents missing from the archive`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Lido.</p>", tocTitle = "Capítulo 1")
                document("notas.xhtml", "<p>Notas fora da leitura.</p>", tocTitle = "Notas", linear = false)
                document("perdido.xhtml", "<p>Não está no arquivo.</p>", tocTitle = "Perdido")
                document("c2.xhtml", "<p>Também lido.</p>", tocTitle = "Capítulo 2")
            }
        fixture.remove(fixture.pathOf("perdido.xhtml"))

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters)
            .containsExactly(Chapter("Capítulo 1", "Lido."), Chapter("Capítulo 2", "Também lido."))
            .inOrder()
    }

    @Test
    fun `never matches manifest items that have no id`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Lido.</p>", tocTitle = "Capítulo 1", tocHref = "outro.xhtml")
                extraManifest += "<item href=\"sem-id.xhtml\" media-type=\"application/xhtml+xml\"/>"
                extraManifest += "<item href=\"toc.ncx\" media-type=\"text/plain\"/>"
                extraSpine += "<itemref idref=\" \"/>"
                entry(pathOf("sem-id.xhtml"), xhtml("<p>Fora do livro.</p>"))
                entry(pathOf("toc.ncx"), ncx(navPoint("Do NCX sem id", "c1.xhtml")))
            }

        assertThat(parseBook(fixture.writeTo(file())).chapters).containsExactly(Chapter(null, "Lido."))
    }

    @Test
    fun `leaves footnote references and footnote bodies out of the chapter text`() {
        val body =
            "<p>Era noite<a epub:type=\"noteref\" href=\"#n1\">1</a> e chovia<sup>2</sup>.</p>" +
                "<aside epub:type=\"footnote\" id=\"n1\"><p>Nota de rodapé.</p></aside><p>Depois.</p>"
        val fixture = EpubFixture().apply { document("c1.xhtml", body, tocTitle = "Capítulo 1") }

        val text = parseBook(fixture.writeTo(file())).chapters.single().text

        assertThat(text).isEqualTo("Era noite e chovia.\n\nDepois.")
    }

    @Test
    fun `preserves Portuguese text, punctuation and dialogue in UTF-8 and declared encodings`() {
        val fixture =
            EpubFixture().apply {
                document(
                    "c1.xhtml",
                    "<p>— Não há ninguém aí? — perguntou João, com o coração aos saltos.</p>" +
                        "<p>Ação&nbsp;e reação: «até já»… Pôr-do-sol, maçã, pão.</p>",
                    tocTitle = "Capítulo 1",
                )
                document("c2.xhtml", "", tocTitle = "Capítulo 2")
            }
        val latin1 =
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><html><body><p>Lição de português.</p></body></html>"
        fixture.entry(fixture.pathOf("c2.xhtml"), latin1.toByteArray(Charsets.ISO_8859_1))

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters.map { it.text })
            .containsExactly(
                "— Não há ninguém aí? — perguntou João, com o coração aos saltos.\n\n" +
                    "Ação e reação: «até já»… Pôr-do-sol, maçã, pão.",
                "Lição de português.",
            ).inOrder()
    }

    @Test
    fun `reads the first title, every creator, the language and a plain-text description`() {
        val fixture =
            EpubFixture().apply {
                title = "Memórias de Teste"
                extraMetadata += "<dc:title>Subtítulo ignorado</dc:title>"
                creators += listOf("Segundo Autor", "  ", "Autora Fictícia")
                language = "pt_BR"
                description = "&lt;p&gt;Uma &lt;b&gt;história&lt;/b&gt; de ciúme.&lt;/p&gt;&lt;p&gt;Fim.&lt;/p&gt;"
                document("c1.xhtml", "<p>Texto.</p>")
            }

        val metadata = parseBook(fixture.writeTo(file())).metadata

        assertThat(metadata)
            .isEqualTo(
                BookMetadata(
                    title = "Memórias de Teste",
                    authors = listOf("Autora Fictícia", "Segundo Autor"),
                    language = "pt-BR",
                    description = "Uma história de ciúme.\n\nFim.",
                ),
            )
    }

    @Test
    fun `reads descriptions written as child elements`() {
        val fixture =
            EpubFixture().apply {
                description = "<p>Primeiro <em>parágrafo</em>.</p><p>Segundo.</p>"
                document("c1.xhtml", "<p>Texto.</p>")
            }

        val description = parseBook(fixture.writeTo(file())).metadata.description

        assertThat(description).isEqualTo("Primeiro parágrafo.\n\nSegundo.")
    }

    @Test
    fun `uses the fallback title when the package has no title or a blank one`() {
        val untitled =
            EpubFixture().apply {
                title = null
                creators.clear()
                language = null
                document("c1.xhtml", "<p>Texto.</p>")
            }
        val blank =
            EpubFixture().apply {
                title = " \n "
                document("c1.xhtml", "<p>Texto.</p>")
            }

        assertThat(parseBook(untitled.writeTo(file("sem-titulo.epub"))).metadata)
            .isEqualTo(BookMetadata(FALLBACK_TITLE))
        assertThat(parseBook(blank.writeTo(file("titulo-vazio.epub"))).metadata.title).isEqualTo(FALLBACK_TITLE)
    }

    @Test
    fun `reads an EPUB 3 cover image`() {
        val image = byteArrayOf(-1, -40, -1, -32, 0, 16)
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraManifest += manifestItem("capa", "imagens/capa.jpg", "image/jpeg", properties = "cover-image")
                entry(pathOf("imagens/capa.jpg"), image)
            }

        assertThat(parseBook(fixture.writeTo(file())).cover).isEqualTo(CoverImage("image/jpeg", image))
    }

    @Test
    fun `reads an EPUB 2 cover image named by the cover meta`() {
        val image = byteArrayOf(-119, 80, 78, 71)
        val fixture =
            EpubFixture(epub3 = false).apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraMetadata += "<meta name=\"cover\" content=\"capa-img\"/>"
                extraManifest += manifestItem("capa-img", "capa.png", "image/png")
                entry(pathOf("capa.png"), image)
            }

        assertThat(parseBook(fixture.writeTo(file())).cover).isEqualTo(CoverImage("image/png", image))
    }

    @Test
    fun `accepts only image covers that exist in the archive`() {
        val image = byteArrayOf(71, 73, 70)
        val withPageCover =
            EpubFixture(epub3 = false).apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraMetadata += "<meta name=\"cover\" content=\"doc1\"/>"
            }
        val withMissingCover =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraManifest += manifestItem("capa", "capa.jpg", "image/jpeg", properties = "cover-image")
            }
        val withNonImageFirst =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraManifest += manifestItem("p", "c1.xhtml", "application/xhtml+xml", properties = "cover-image")
                extraManifest += manifestItem("img", "capa.gif", "image/gif")
                extraMetadata += "<meta name=\"cover\" content=\"img\"/>"
                entry(pathOf("capa.gif"), image)
            }

        assertThat(parseBook(withPageCover.writeTo(file("pagina.epub"))).cover).isNull()
        assertThat(parseBook(withMissingCover.writeTo(file("sem-arquivo.epub"))).cover).isNull()
        assertThat(parseBook(withNonImageFirst.writeTo(file("imagem.epub"))).cover)
            .isEqualTo(CoverImage("image/gif", image))
    }

    @Test
    fun `finds percent-encoded file names and a package document at the archive root`() {
        val fixture =
            EpubFixture().apply {
                packagePath = "content.opf"
                document(
                    "Capítulo 1.xhtml",
                    "<p>Nome com acento e espaço.</p>",
                    tocTitle = "Capítulo 1",
                    href = "Cap%C3%ADtulo%201.xhtml",
                )
            }

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.chapters).containsExactly(Chapter("Capítulo 1", "Nome com acento e espaço."))
    }

    @Test
    fun `understands package documents with prefixed elements`() {
        val fixture = EpubFixture(epub3 = false).apply { document("c1.xhtml", "<p>Texto prefixado.</p>") }
        val prefixed =
            "<opf:package xmlns:opf=\"http://www.idpf.org/2007/opf\" version=\"2.0\">" +
                "<opf:metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:Title>Prefixado</dc:Title>" +
                "</opf:metadata><opf:manifest>" +
                "<opf:item id=\"c1\" href=\"c1.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                "</opf:manifest><opf:spine><opf:itemref idref=\"c1\"/></opf:spine></opf:package>"
        fixture.entry(fixture.packagePath, prefixed)

        val book = parseBook(fixture.writeTo(file()))

        assertThat(book.metadata.title).isEqualTo("Prefixado")
        assertThat(book.chapters).containsExactly(Chapter(null, "Texto prefixado."))
    }

    private fun file(name: String = "livro.epub"): File = File(directory, name)
}
