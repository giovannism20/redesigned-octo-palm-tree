package com.giovannimartins.rreader.catalog

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock that tests move forward by hand. */
internal class MutableClock(
    private var now: Instant = Instant.parse("2026-11-27T12:00:00Z"),
) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** Feeds shaped like Project Gutenberg's OPDS search results, written in the tests. */
internal object TestFeeds {
    /** A page of search results listing [books] (ebook number, title, author), with a [next] link if given. */
    fun page(
        vararg books: Triple<Int, String, String>,
        next: String? = null,
    ): ByteArray {
        val entries =
            books.joinToString("\n") { (id, title, author) ->
                "<entry><id>https://www.gutenberg.org/ebooks/$id.opds</id><title>$title</title>" +
                    "<content type=\"text\">$author</content>" +
                    "<link type=\"$OPDS\" rel=\"subsection\" href=\"/ebooks/$id.opds\"/></entry>"
            }
        val nextLink =
            next?.let { "<link rel=\"next\" type=\"$OPDS\" href=\"${it.replace("&", "&amp;")}\"/>" }.orEmpty()
        return (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Books</title>$nextLink\n$entries\n</feed>"
        ).toByteArray()
    }

    private const val OPDS = "application/atom+xml;profile=opds-catalog"
}

/** A small curated list, in the asset's format. */
internal const val CURATED_JSON =
    """[
  {"id": 55752, "title": "Dom Casmurro", "author": "Machado de Assis", "description": "Bento relembra Capitu."},
  {"id": 67740, "title": "Iracema", "author": "José de Alencar", "description": "A virgem dos lábios de mel."}
]"""

internal val DOM_CASMURRO = CatalogEntry(55752, "Dom Casmurro", "Machado de Assis", "Bento relembra Capitu.")
internal val IRACEMA = CatalogEntry(67740, "Iracema", "José de Alencar", "A virgem dos lábios de mel.")
