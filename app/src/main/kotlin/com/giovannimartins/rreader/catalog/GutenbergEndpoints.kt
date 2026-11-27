package com.giovannimartins.rreader.catalog

import java.net.URLEncoder

/**
 * The Project Gutenberg addresses the catalogue uses, checked against the live site in November 2026 (ADR 0017).
 * Both are on `www.gutenberg.org`, which [CatalogHosts] allows.
 */
object GutenbergEndpoints {
    private const val SITE = "https://www.gutenberg.org"

    /** Gutenberg's search filter for books in Portuguese, added to every search. */
    private const val PORTUGUESE = "l.pt"

    /** The first page of the OPDS search results for [terms] among books in Portuguese (25 per page). */
    fun search(terms: String): String =
        "$SITE/ebooks/search.opds/?query=" + URLEncoder.encode("$terms $PORTUGUESE", "UTF-8")

    /** The EPUB 3 edition, with images and cover, of ebook [id]; it redirects to the file under `/cache/epub/`. */
    fun epub(id: Int): String = "$SITE/ebooks/$id.epub3.images"
}
