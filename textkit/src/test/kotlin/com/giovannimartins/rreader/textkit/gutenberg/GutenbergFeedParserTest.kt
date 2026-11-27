package com.giovannimartins.rreader.textkit.gutenberg

import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/** Feeds shaped like Project Gutenberg's OPDS search results and book feeds, written in the tests. */
class GutenbergFeedParserTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `reads the books of a page of search results and the link to the next page`() {
        val xml =
            feed(
                searchEntry(55752, "Dom Casmurro", "Machado de Assis"),
                searchEntry(54829, "Memorias Posthumas de Braz Cubas", "Machado de Assis"),
                next = "/ebooks/search.opds/?query=machado+l.pt&start_index=26",
            )

        val page = GutenbergFeedParser.parse(xml)

        assertThat(page)
            .isEqualTo(
                FeedPage(
                    books =
                        listOf(
                            FeedBook(55752, "Dom Casmurro", "Machado de Assis"),
                            FeedBook(54829, "Memorias Posthumas de Braz Cubas", "Machado de Assis"),
                        ),
                    next = "/ebooks/search.opds/?query=machado+l.pt&start_index=26",
                ),
            )
    }

    @Test
    fun `the last page has no next page`() {
        val page = GutenbergFeedParser.parse(feed(searchEntry(67740, "Iracema", "José de Alencar")))

        assertThat(page?.next).isNull()
    }

    @Test
    fun `leaves out entries that are not books, repeated books and books without a title`() {
        val xml =
            feed(
                "<entry><id>https://www.gutenberg.org/ebooks/author/9685.opds</id><title>Machado de Assis</title>" +
                    "<link rel=\"subsection\" href=\"/ebooks/author/9685.opds\"/></entry>",
                "<entry><id>sort</id><title>Sort Alphabetically by Title</title>" +
                    "<link rel=\"http://opds-spec.org/sort/new\" " +
                    "href=\"/ebooks/search.opds/?sort_order=title\"/></entry>",
                searchEntry(55752, "Dom Casmurro", "Machado de Assis"),
                searchEntry(55752, "Dom Casmurro", "Machado de Assis"),
                searchEntry(55682, "  ", "Machado de Assis"),
                searchEntry(0, "Livro sem número", null),
            )

        val books = GutenbergFeedParser.parse(xml)?.books

        assertThat(books).containsExactly(FeedBook(55752, "Dom Casmurro", "Machado de Assis"))
    }

    @Test
    fun `takes the author's name from a book feed rather than its description`() {
        val xml =
            feed(
                "<entry><title>Dom Casmurro</title>" +
                    "<content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\"><p>This edition has images." +
                    "</p></div></content><id>urn:gutenberg:55752:3</id><author><name>Machado de Assis</name></author>" +
                    "<link type=\"application/epub+zip\" rel=\"http://opds-spec.org/acquisition\" " +
                    "href=\"https://www.gutenberg.org/ebooks/55752.epub3.images\"/>" +
                    "<link rel=\"subsection\" href=\"https://www.gutenberg.org/ebooks/55752.opds\"/></entry>",
            )

        val books = GutenbergFeedParser.parse(xml)?.books

        assertThat(books).containsExactly(FeedBook(55752, "Dom Casmurro", "Machado de Assis"))
    }

    @Test
    fun `finds the book number in the entry id when there is no subsection link`() {
        val xml =
            feed("<entry><id> https://www.gutenberg.org/ebooks/16425.opds </id><title>Amor de Perdição</title></entry>")

        assertThat(GutenbergFeedParser.parse(xml)?.books).containsExactly(FeedBook(16425, "Amor de Perdição", null))
    }

    @Test
    fun `puts titles on one line and cleans them like book text`() {
        val title = "Iracema\n   com uma noticia biographica do auctor${Char(0xAD)}"
        val xml = feed(searchEntry(67740, title, "José de\nAlencar"))

        assertThat(GutenbergFeedParser.parse(xml)?.books)
            .containsExactly(FeedBook(67740, "Iracema com uma noticia biographica do auctor", "José de Alencar"))
    }

    @Test
    fun `SR-13 cuts a huge title and author to their limits`() {
        val xml = feed(searchEntry(55752, "Dom Casmurro ".repeat(2_000), "Machado de Assis ".repeat(2_000)))

        val book = GutenbergFeedParser.parse(xml)!!.books.single()

        assertThat(book.title.length).isAtMost(MetadataLimits.MAX_TITLE_LENGTH)
        assertThat(book.title).startsWith("Dom Casmurro Dom Casmurro")
        assertThat(book.title).endsWith("…")
        assertThat(book.author!!.length).isAtMost(MetadataLimits.MAX_AUTHOR_LENGTH)
    }

    @Test
    fun `reads at most a hundred books`() {
        val entries = (1..GutenbergFeedParser.MAX_BOOKS + 20).map { searchEntry(it, "Livro $it", null) }

        val books = GutenbergFeedParser.parse(feed(*entries.toTypedArray()))?.books

        assertThat(books).hasSize(GutenbergFeedParser.MAX_BOOKS)
        assertThat(books?.last()?.id).isEqualTo(GutenbergFeedParser.MAX_BOOKS)
    }

    @Test
    fun `an empty feed has no books`() {
        assertThat(GutenbergFeedParser.parse(feed())).isEqualTo(FeedPage(emptyList(), null))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "Service Unavailable",
            "<!DOCTYPE html><html><head><title>Temporarily Down</title></head><body><h1>Down</h1></body></html>",
            "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Novos livros</title></channel></rss>",
            "<feed",
        ],
    )
    fun `answers null for anything but an Atom feed`(text: String) {
        assertThat(GutenbergFeedParser.parse(text)).isNull()
    }

    @Test
    fun `SR-3 a feed neither loads external entities nor expands declared ones`() {
        val secret = File(directory, "segredo.txt").apply { writeText("SEGREDO-DO-DISCO") }
        val laughs = (2..6).joinToString("") { level -> "<!ENTITY lol$level \"${"&lol${level - 1};".repeat(10)}\">" }
        val doctype = "<!DOCTYPE feed [<!ENTITY xxe SYSTEM \"${secret.toURI()}\"><!ENTITY lol1 \"lol\">$laughs]>"
        val xml = "<?xml version=\"1.0\"?>$doctype" + feed(searchEntry(1, "&xxe;", "&lol6;")).substringAfter("?>")

        val book = GutenbergFeedParser.parse(xml)?.books?.single()

        assertThat(book).isEqualTo(FeedBook(1, "&xxe;", "&lol6;"))
    }

    private fun feed(
        vararg entries: String,
        next: String? = null,
    ): String =
        buildList {
            add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            add(
                "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:opds=\"http://opds-spec.org/2010/catalog\" " +
                    "xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\">",
            )
            add("<id>http://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt</id>")
            add("<title>Books: machado l.pt</title>")
            add("<link rel=\"self\" type=\"$OPDS\" href=\"/ebooks/search.opds/?query=machado+l.pt\"/>")
            next?.let { add("<link rel=\"next\" title=\"Next Page\" type=\"$OPDS\" href=\"$it\"/>") }
            add("<opensearch:itemsPerPage>25</opensearch:itemsPerPage>")
            addAll(entries)
            add("</feed>")
        }.joinToString("\n")

    /** An entry as Gutenberg's search results list it: the author as plain-text content. */
    private fun searchEntry(
        id: Int,
        title: String,
        author: String?,
    ): String =
        "<entry><updated>2026-11-15T01:14:13Z</updated><id>https://www.gutenberg.org/ebooks/$id.opds</id>" +
            "<title>$title</title>" +
            author?.let { "<content type=\"text\">$it</content>" }.orEmpty() +
            "<link type=\"$OPDS\" rel=\"subsection\" href=\"/ebooks/$id.opds\"/>" +
            "<link type=\"image/png\" rel=\"http://opds-spec.org/image/thumbnail\" " +
            "href=\"data:image/png;base64,AA==\"/>" +
            "</entry>"

    private companion object {
        const val OPDS = "application/atom+xml;profile=opds-catalog"
    }
}
