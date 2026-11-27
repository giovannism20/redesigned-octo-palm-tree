package com.giovannimartins.rreader.catalog

import java.net.URLEncoder

/**
 * The Project Gutenberg addresses the catalogue uses, checked against the live site and its mirror in November 2026
 * (ADR 0017, ADR 0019). They are on [CatalogHosts.SITE] and [CatalogHosts.MIRROR], which [CatalogHosts] allows.
 */
object GutenbergEndpoints {
    private const val SITE = "https://${CatalogHosts.SITE}"
    private const val MIRROR = "https://${CatalogHosts.MIRROR}"

    /** Gutenberg's search filter for books in Portuguese, added to every search. */
    private const val PORTUGUESE = "l.pt"

    /** The first page of the OPDS search results for [terms] among books in Portuguese (25 per page). */
    fun search(terms: String): String =
        "$SITE/ebooks/search.opds/?query=" + URLEncoder.encode("$terms $PORTUGUESE", "UTF-8")

    /** The EPUB 3 edition, with images and cover, of ebook [id]; it redirects to the file under `/cache/epub/`. */
    fun epub(id: Int): String = "$SITE/ebooks/$id.epub3.images"

    /** The same EPUB 3 file of ebook [id] on the official mirror, served as `application/octet-stream`. */
    fun mirrorEpub(id: Int): String = "$MIRROR/cache/epub/$id/pg$id-images-3.epub"
}
