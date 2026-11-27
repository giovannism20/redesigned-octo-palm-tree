package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The rules [ChapterAssembler] follows, on spine documents built from XHTML. */
class ChapterAssemblerTest {
    @Test
    fun `gives a chapter the headings printed right before the element the table of contents points to`() {
        val body =
            "<h5>I</h5><h4 id=\"c1\">Do título.</h4>${prose("o título")}<hr/>" +
                "<h5> <a id=\"II\">II</a></h5><h4 id=\"c2\">Do livro.</h4>${prose("o livro")}"

        val chapters =
            assemble(listOf(document("livro.xhtml", body)), entry("Do título.", "#c1"), entry("Do livro.", "#c2"))

        assertThat(chapters.map { it.title }).containsExactly("Do título.", "Do livro.").inOrder()
        assertThat(chapters.map { it.firstParagraph() }).containsExactly("I", "II").inOrder()
    }

    @Test
    fun `never moves headings across documents`() {
        val documents =
            listOf(
                document("parte1.xhtml", "${prose("a primeira parte")}<h5>FIM DA PRIMEIRA PARTE</h5>"),
                document("parte2.xhtml", "<h2 id=\"p2\">SEGUNDA PARTE</h2>${prose("a segunda parte")}"),
            )

        val chapters = assemble(documents, entry("Primeira", "parte1.xhtml"), entry("Segunda", "parte2.xhtml#p2"))

        assertThat(chapters.first().paragraphs().last()).isEqualTo("FIM DA PRIMEIRA PARTE")
        assertThat(chapters.last().firstParagraph()).isEqualTo("SEGUNDA PARTE")
    }

    @Test
    fun `uses the chapters nested under parts when they are long enough`() {
        val parts = (1..3).map { part -> partWithChapters(part, chapterLength = 3_500) }

        val chapters =
            assemble(
                listOf(
                    document(
                        "livro.xhtml",
                        parts.joinToString("") {
                            it.first
                        },
                    ),
                ),
                *parts.flatMap { it.second }.toTypedArray(),
            )

        assertThat(chapters.map { it.title })
            .containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3", "Capítulo 4", "Capítulo 5", "Capítulo 6")
            .inOrder()
        assertThat(chapters.map { it.firstParagraph() })
            .containsExactly("PARTE 1", "Capítulo 2", "PARTE 2", "Capítulo 4", "PARTE 3", "Capítulo 6")
            .inOrder()
    }

    @Test
    fun `keeps short numbered sections inside the chapters that hold them`() {
        val body =
            (1..3).joinToString("") { chapter ->
                "<h2 id=\"c$chapter\">Capítulo $chapter</h2>${prose("a abertura $chapter", 500)}" +
                    "<h3 id=\"c$chapter-1\">1</h3>${prose("a cena um", 1_000)}" +
                    "<h3 id=\"c$chapter-2\">2</h3>${prose("a cena dois", 1_000)}"
            }
        val entries =
            (1..3).flatMap { chapter ->
                listOf(
                    entry("Capítulo $chapter", "#c$chapter"),
                    entry("1", "#c$chapter-1", depth = 2),
                    entry("2", "#c$chapter-2", depth = 2),
                )
            }

        val chapters = assemble(listOf(document("livro.xhtml", body)), *entries.toTypedArray())

        assertThat(chapters.map { it.title }).containsExactly("Capítulo 1", "Capítulo 2", "Capítulo 3").inOrder()
        assertThat(chapters.first().paragraphs()).containsAtLeast("1", "2").inOrder()
    }

    @Test
    fun `uses short chapters when the level above holds most of the book`() {
        val chapters = (1..4).joinToString("") { "<h2 id=\"c$it\">$it</h2>${prose("o capítulo $it", 800)}" }
        val entries = listOf(entry("LIVRO", "#t")) + (1..4).map { entry("$it", "#c$it", depth = 2) }

        val book =
            assemble(listOf(document("livro.xhtml", "<h1 id=\"t\">LIVRO</h1>$chapters")), *entries.toTypedArray())

        assertThat(book.map { it.title }).containsExactly("1", "2", "3", "4").inOrder()
        assertThat(book.first().firstParagraph()).isEqualTo("LIVRO")
    }

    @Test
    fun `uses short chapters when the level above is made of sections longer than an hour of listening`() {
        val parts = (1..3).map { part -> partWithChapters(part, chapterLength = 2_000, chapters = 26) }

        val chapters =
            assemble(
                listOf(
                    document(
                        "livro.xhtml",
                        parts.joinToString("") {
                            it.first
                        },
                    ),
                ),
                *parts.flatMap { it.second }.toTypedArray(),
            )

        assertThat(chapters).hasSize(78)
        assertThat(chapters[26].firstParagraph()).isEqualTo("PARTE 2")
    }

    @Test
    fun `joins short sections to a neighbour in the same document, never across documents`() {
        val documents =
            listOf(
                document("rosto.xhtml", "<h1 id=\"t\">LIVRO</h1><h2 id=\"a\">POR</h2><h2 id=\"b\">AUTORA</h2>"),
                document(
                    "c1.xhtml",
                    "<h2 id=\"c1\">Capítulo 1</h2>${prose("o começo")}<p>Pausa.</p><h2 id=\"fim\">FIM</h2>",
                ),
                document("notas.xhtml", "<p>Notas curtas.</p>"),
            )
        val entries =
            arrayOf(
                entry("LIVRO", "rosto.xhtml#t"),
                entry("POR", "rosto.xhtml#a"),
                entry("AUTORA", "rosto.xhtml#b"),
                entry("Capítulo 1", "c1.xhtml#c1"),
                entry("FIM", "c1.xhtml#fim"),
                entry("Notas", "notas.xhtml"),
            )

        val chapters = assemble(documents, *entries)

        assertThat(chapters.map { it.title }).containsExactly("LIVRO", "Capítulo 1", "Notas").inOrder()
        assertThat(chapters[0].text).isEqualTo("LIVRO\n\nPOR\n\nAUTORA")
        assertThat(chapters[1].paragraphs().takeLast(2)).containsExactly("Pausa.", "FIM").inOrder()
    }

    /** A part of [chapters] chapters of [chapterLength] characters, and its table-of-contents entries. */
    private fun partWithChapters(
        part: Int,
        chapterLength: Int,
        chapters: Int = 2,
    ): Pair<String, List<TocEntry>> {
        val numbers = (1..chapters).map { (part - 1) * chapters + it }
        val body =
            "<h1 id=\"p$part\">PARTE $part</h1>" +
                numbers.joinToString(
                    "",
                ) { "<h2 id=\"c$it\">Capítulo $it</h2>${prose("o capítulo $it", chapterLength)}" }
        val entries =
            listOf(entry("PARTE $part", "#p$part")) + numbers.map { entry("Capítulo $it", "#c$it", depth = 2) }
        return body to entries
    }

    private fun document(
        path: String,
        body: String,
    ): SpineDocument = SpineDocument(path, ContentExtractor.extract(SafeXml.parse(xhtml(body).toByteArray(), path)))

    /** An entry for [href]: `#id` for an element of `livro.xhtml`, `file#id` or a file name. */
    private fun entry(
        title: String?,
        href: String,
        depth: Int = 1,
    ): TocEntry {
        val path = href.substringBefore('#').ifEmpty { "livro.xhtml" }
        return TocEntry(title, path, href.substringAfter('#', "").ifEmpty { null }, depth)
    }

    private fun assemble(
        documents: List<SpineDocument>,
        vararg entries: TocEntry,
    ): List<Chapter> =
        ChapterAssembler.assemble(documents, if (entries.isEmpty()) emptySequence() else sequenceOf(entries.toList()))

    private fun Chapter.paragraphs(): List<String> = paragraphRanges().map { text.substring(it.start, it.end) }

    private fun Chapter.firstParagraph(): String = paragraphs().first()
}
