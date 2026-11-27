package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Chapters of books that keep several chapters in one file, found through anchors or headings. */
class EpubChapterTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `splits a single-file book at the elements its table of contents points to`() {
        val body =
            "<h1 id=\"rosto\">Livro de Teste</h1><p>Autora Fictícia</p>" +
                "<h2 id=\"c1\">Capítulo 1</h2>${prose("a partida")}" +
                "<h2 id=\"c2\">Capítulo 2</h2>${prose("a viagem")}" +
                "<h2 id=\"c3\">Capítulo 3</h2>${prose("a chegada")}"
        val fixture =
            EpubFixture().apply {
                document("livro.xhtml", body)
                tableOfContents(
                    navItem("Livro de Teste", "livro.xhtml#rosto"),
                    navItem("Um", "livro.xhtml#c1"),
                    navItem("Dois", "livro.xhtml#c2"),
                    navItem("Três", "livro.xhtml#c3"),
                )
            }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.title }).containsExactly("Um", "Dois", "Três").inOrder()
        assertThat(chapters[0].paragraphs().take(3))
            .containsExactly("Livro de Teste", "Autora Fictícia", "Capítulo 1")
            .inOrder()
        assertThat(
            chapters.map { it.paragraphs().first() }.drop(1),
        ).containsExactly("Capítulo 2", "Capítulo 3").inOrder()
    }

    @Test
    fun `mixes entries for whole files with entries for elements inside them`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<h2>Capítulo 1</h2>${prose("o começo")}", tocTitle = "Um")
                document(
                    "c2.xhtml",
                    "<p>Fim do capítulo um.</p><h2 id=\"a\">Capítulo 2</h2>${prose("o meio")}" +
                        "<h2 id=\"b\">Capítulo 3</h2>${prose("a virada")}",
                )
                document("c3.xhtml", "<h2>Capítulo 4</h2>${prose("o fim")}", tocTitle = "Quatro")
                tableOfContents(
                    navItem("Um", "c1.xhtml"),
                    navItem("Dois", "c2.xhtml#a"),
                    navItem("Três", "c2.xhtml#b"),
                    navItem("Quatro", "c3.xhtml"),
                )
            }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.title }).containsExactly("Um", "Dois", "Três", "Quatro").inOrder()
        assertThat(chapters[0].paragraphs().last()).isEqualTo("Fim do capítulo um.")
        assertThat(chapters.map { it.paragraphs().first() })
            .containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3", "Capítulo 4")
            .inOrder()
    }

    @Test
    fun `follows a nested NCX down to its chapters, leaving short numbered scenes inside them`() {
        val body =
            "<h1 id=\"p1\">PARTE I</h1>${chapterWithScenes(1)}${chapterWithScenes(2)}" +
                "<h1 id=\"p2\">PARTE II</h1>${chapterWithScenes(3)}${chapterWithScenes(4)}"
        val fixture = EpubFixture(epub3 = false).apply { document("livro.xhtml", body) }
        val toc =
            ncx(
                navPoint("PARTE I", "livro.xhtml#p1", chapterPoint(1), chapterPoint(2)),
                navPoint("PARTE II", "livro.xhtml#p2", chapterPoint(3), chapterPoint(4)),
            )
        fixture.entry(fixture.pathOf(EpubFixture.NCX_FILE), toc)

        val book = parseBook(fixture.writeTo(file())).chapters

        assertThat(
            book.map { it.title },
        ).containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3", "Capítulo 4").inOrder()
        assertThat(book.map { it.paragraphs().first() })
            .containsExactly("PARTE I", "Capítulo 2", "PARTE II", "Capítulo 4")
            .inOrder()
        assertThat(book[1].paragraphs()).containsAtLeast("Capítulo 2", "1", "2").inOrder()
    }

    @Test
    fun `never starts chapters at entries nested deeper than eight levels`() {
        val sections = 12
        val body = (1..sections).joinToString("") { "<h2 id=\"s$it\">Seção $it</h2>${prose("a seção $it", 3_500)}" }
        val nested =
            (sections downTo 1).fold(emptyArray<String>()) { inner, level ->
                arrayOf(navItem("Seção $level", "livro.xhtml#s$level", *inner))
            }
        val fixture =
            EpubFixture().apply {
                document("livro.xhtml", body)
                tableOfContents(*nested)
            }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.title })
            .containsExactlyElementsIn((1..TocDepth.MAX_LEVELS).map { "Seção $it" })
            .inOrder()
    }

    @Test
    fun `starts chapters at anchors on inline and empty elements`() {
        val body =
            "<p><a id=\"c1\"/>Capítulo 1</p>${prose("um")}<p>Última frase do capítulo um.<a id=\"c2\"/></p>" +
                "<p>Capítulo 2</p>${prose("dois")}<div id=\"c3\"></div><p>Capítulo 3</p>${prose("três")}" +
                "<h2><span id=\"c4\">Capítulo</span> 4</h2>${prose("quatro")}"
        val fixture =
            EpubFixture().apply {
                document("livro.xhtml", body)
                tableOfContents(*(1..4).map { navItem("Parte $it", "livro.xhtml#c$it") }.toTypedArray())
            }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.paragraphs().first() })
            .containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3", "Capítulo 4")
            .inOrder()
        assertThat(chapters[0].paragraphs().last()).isEqualTo("Última frase do capítulo um.")
    }

    @Test
    fun `points an entry whose element is missing to the start of its file`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<h2 id=\"c1\">Capítulo 1</h2>${prose("um")}")
                document("c2.xhtml", "<h2>Capítulo 2</h2>${prose("dois")}<h2 id=\"c3\">Capítulo 3</h2>${prose("três")}")
                tableOfContents(
                    navItem("Um", "c1.xhtml#nao-existe"),
                    navItem("Dois", "c2.xhtml#falta"),
                    navItem("Três", "c2.xhtml#c3"),
                )
            }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.title }).containsExactly("Um", "Dois", "Três").inOrder()
        assertThat(chapters.map { it.paragraphs().first() })
            .containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3")
            .inOrder()
    }

    @Test
    fun `splits a long file at its chapter headings when the table of contents does not reach them`() {
        val headings = listOf("CAPITULO PRIMEIRO", "CAPITULO II", "CAPITULO III", "CAPITULO IV")
        val body =
            "<h1>LIVRO</h1><p>Autora Fictícia</p>" + headings.joinToString("") { "<h5>$it</h5>${prose(it, 13_000)}" }
        val fixture = EpubFixture().apply { document("livro.xhtml", body, tocTitle = "Livro") }

        val chapters = parseBook(fixture.writeTo(file())).chapters

        assertThat(chapters.map { it.title }).containsExactlyElementsIn(headings).inOrder()
        assertThat(
            chapters[0].paragraphs().take(3),
        ).containsExactly("LIVRO", "Autora Fictícia", "CAPITULO PRIMEIRO").inOrder()
    }

    /** Chapter [number] with two short numbered scenes, as `<h2 id="cN">` and `<h3 id="cN-1">`, `<h3 id="cN-2">`. */
    private fun chapterWithScenes(number: Int): String =
        "<h2 id=\"c$number\">Capítulo $number</h2>${prose("a abertura $number", 1_500)}" +
            "<h3 id=\"c$number-1\">1</h3>${prose(
                "a cena um",
                800,
            )}<h3 id=\"c$number-2\">2</h3>${prose("a cena dois", 800)}"

    /** The NCX point of chapter [number] and, nested in it, those of its two scenes. */
    private fun chapterPoint(number: Int): String =
        navPoint(
            "Capítulo $number",
            "livro.xhtml#c$number",
            navPoint("1", "livro.xhtml#c$number-1"),
            navPoint("2", "livro.xhtml#c$number-2"),
        )

    private fun Chapter.paragraphs(): List<String> = paragraphRanges().map { text.substring(it.start, it.end) }

    private fun file(): File = File(directory, "livro.epub")
}
