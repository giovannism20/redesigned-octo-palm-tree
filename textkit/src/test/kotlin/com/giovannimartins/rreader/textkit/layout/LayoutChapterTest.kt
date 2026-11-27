package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Chapters from the outline, from headings and from page ranges (see [ChapterSplitter]). */
class LayoutChapterTest {
    @Test
    fun `starts outline chapters at the first line left on their page, after the front matter`() {
        val pages =
            listOf(
                page(0) {
                    header("DOM CASMURRO")
                    centered("DOM CASMURRO", size = 16f)
                    centered("Machado de Assis")
                },
                storyPage(1, "Uma noite destas", header = "DOM CASMURRO"),
                storyPage(2, "Encontrei no trem", header = "DOM CASMURRO"),
                storyPage(3, "Agora que expliquei", header = "DOM CASMURRO"),
            )
        val outline = listOf(OutlineEntry("Do título", 1), OutlineEntry("Do livro", 3))

        assertThat(buildBook(pages, outline).chapters)
            .containsExactly(
                Chapter(null, "DOM CASMURRO\n\nMachado de Assis"),
                Chapter("Do título", storyText("Uma noite destas") + "\n\n" + storyText("Encontrei no trem")),
                Chapter("Do livro", storyText("Agora que expliquei")),
            ).inOrder()
    }

    @Test
    fun `starts a chapter in the middle of a page at the entry's position`() {
        val pages =
            listOf(
                page(0) {
                    story("O primeiro capítulo é curto")
                    gap(2f)
                    story("O segundo começa no meio")
                },
                storyPage(1, "E continua na página seguinte"),
            )

        val chapters = buildBook(pages, listOf(OutlineEntry("Um", 0), OutlineEntry("Dois", 0, top = 150f))).chapters

        assertThat(chapters.map { it.title }).containsExactly("Um", "Dois").inOrder()
        assertThat(chapters[0].text).isEqualTo(storyText("O primeiro capítulo é curto"))
        assertThat(chapters[1].text).startsWith(storyText("O segundo começa no meio"))
    }

    @Test
    fun `keeps the first title of entries for the same place and ignores entries outside the document`() {
        val pages = (0..2).map { storyPage(it, "Texto da página ${it + 1}") }
        val outline =
            listOf(
                OutlineEntry("Parte I", 0),
                OutlineEntry("Capítulo 1", 0),
                OutlineEntry(" \t ", 2),
                OutlineEntry("Depois do fim", 9),
                OutlineEntry("Antes do começo", -1),
            )

        val chapters = buildBook(pages, outline).chapters

        assertThat(chapters.map { it.title }).containsExactly("Parte I", null).inOrder()
        assertThat(chapters[1].text).isEqualTo(storyText("Texto da página 3"))
    }

    @Test
    fun `starts a chapter that points to an empty page at the next page with text`() {
        val pages = listOf(storyPage(0, "Antes do capítulo"), page(1) {}, storyPage(2, "Primeiro texto"))

        val chapters = buildBook(pages, listOf(OutlineEntry("Início", 0), OutlineEntry("Capítulo", 1))).chapters

        assertThat(chapters)
            .containsExactly(
                Chapter("Início", storyText("Antes do capítulo")),
                Chapter("Capítulo", storyText("Primeiro texto")),
            ).inOrder()
    }

    @Test
    fun `uses headings when a long document's outline has a single entry`() {
        val pages =
            (0..11).map { index ->
                page(index) {
                    if (index % 4 == 0) centered("CAPÍTULO ${index / 4 + 1}")
                    story("Texto narrativo da página ${index + 1}")
                }
            }

        val chapters = buildBook(pages, listOf(OutlineEntry("Livro", 0))).chapters

        assertThat(chapters.map { it.title }).containsExactly("CAPÍTULO 1", "CAPÍTULO 2", "CAPÍTULO 3").inOrder()
        assertThat(chapters[0].text).startsWith("CAPÍTULO 1\n\n${storyText("Texto narrativo da página 1")}")
    }

    @Test
    fun `detects headings by their wording and keeps the text before them`() {
        val pages =
            listOf(
                page(0) {
                    story("Uma advertência ao leitor")
                    gap()
                    line("Capítulo I: A partida", left = 120f, right = 230f)
                    gap()
                    story("Partimos cedo")
                },
                page(1) {
                    centered("PARTE SEGUNDA")
                    gap()
                    story("A volta foi mais longa")
                },
            )

        assertThat(buildBook(pages).chapters)
            .containsExactly(
                Chapter(null, storyText("Uma advertência ao leitor")),
                Chapter("Capítulo I: A partida", "Capítulo I: A partida\n\n${storyText("Partimos cedo")}"),
                Chapter("PARTE SEGUNDA", "PARTE SEGUNDA\n\n${storyText("A volta foi mais longa")}"),
            ).inOrder()
    }

    @Test
    fun `keeps a part title and the chapter heading that follows it in one chapter`() {
        val pages =
            listOf(
                page(0) {
                    centered("PARTE I")
                    gap(3f)
                    centered("CAPÍTULO 1")
                    gap()
                    story("O começo de tudo")
                },
                page(1) {
                    centered("CAPÍTULO 2")
                    gap()
                    story("A continuação")
                },
            )

        val chapters = buildBook(pages).chapters

        assertThat(chapters.map { it.title }).containsExactly("PARTE I", "CAPÍTULO 2").inOrder()
        assertThat(chapters[0].text).isEqualTo("PARTE I\n\nCAPÍTULO 1\n\n${storyText("O começo de tudo")}")
    }

    @Test
    fun `detects headings in a large font and joins a heading broken across lines`() {
        val pages =
            listOf(
                page(0) {
                    centered("O ALIENISTA", size = 18f)
                    gap(2f)
                    centered("DE COMO ITAGUAÍ GANHOU", size = 14f)
                    centered("UM MANICÔMIO", size = 14f)
                    gap()
                    story("As crônicas da vila de Itaguaí")
                },
                page(1) {
                    centered("TORRENTES DE LOUCOS", size = 14f)
                    gap()
                    story("Três dias depois")
                },
            )

        val chapters = buildBook(pages).chapters

        assertThat(chapters.map { it.title }).containsExactly("O ALIENISTA", "TORRENTES DE LOUCOS").inOrder()
        assertThat(chapters[0].text.split("\n\n"))
            .containsExactly(
                "O ALIENISTA",
                "DE COMO ITAGUAÍ GANHOU UM MANICÔMIO",
                storyText("As crônicas da vila de Itaguaí"),
            ).inOrder()
    }

    @Test
    fun `ignores heading words inside paragraphs and in a table of contents`() {
        val contents =
            page(0) {
                story("Índice do volume")
                full("Capítulo 1 .................... 5")
                full("Capítulo 2 … 9")
                gap()
                full("O regulamento manda ler com atenção o", indent = true)
                full("Capítulo 3 do regulamento, que trata das")
                last("multas e de mais nada.")
            }

        assertThat(buildBook(listOf(contents)).chapters.map { it.title }).containsExactly(null)
    }

    @Test
    fun `falls back to untitled chapters of ten pages that start at paragraph boundaries`() {
        val pages =
            (0..24).map { index ->
                page(index) {
                    if (index == 10) last("continua no alto da página onze.")
                    story("Texto sem títulos da página ${index + 1}")
                    if (index == 9) full("e este parágrafo", indent = true)
                }
            }

        val chapters = buildBook(pages).chapters

        assertThat(chapters.map { it.title }).containsExactly(null, null, null)
        assertThat(chapters[0].text).endsWith("e este parágrafo continua no alto da página onze.")
        assertThat(chapters[1].text).startsWith(storyText("Texto sem títulos da página 11"))
        assertThat(chapters[2].text).startsWith(storyText("Texto sem títulos da página 21"))
    }

    @Test
    fun `makes a short document without outline or headings a single chapter`() {
        val pages = (0..2).map { storyPage(it, "Um conto breve, página ${it + 1}") }

        assertThat(buildBook(pages).chapters.single().title).isNull()
    }

    private fun storyPage(
        index: Int,
        opening: String,
        header: String? = null,
    ): PageLayout =
        page(index) {
            header?.let(::header)
            story(opening)
        }
}
