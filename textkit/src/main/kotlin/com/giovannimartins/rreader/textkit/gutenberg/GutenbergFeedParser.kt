package com.giovannimartins.rreader.textkit.gutenberg

import com.giovannimartins.rreader.textkit.TextCleaner
import com.giovannimartins.rreader.textkit.epub.SafeXml
import com.giovannimartins.rreader.textkit.epub.attributeTokens
import com.giovannimartins.rreader.textkit.epub.childrenNamed
import com.giovannimartins.rreader.textkit.epub.localName
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import org.jsoup.nodes.Element

/** A book listed in a Project Gutenberg OPDS feed. */
public data class FeedBook(
    /** Gutenberg's ebook number, as in `https://www.gutenberg.org/ebooks/55752`; always positive. */
    val id: Int,
    /** The title on one line; never blank, at most [MetadataLimits.MAX_TITLE_LENGTH] characters. */
    val title: String,
    /** The author as the feed names it, at most [MetadataLimits.MAX_AUTHOR_LENGTH] characters, or null. */
    val author: String?,
)

/** One page of a Project Gutenberg OPDS feed, such as a page of search results. */
public data class FeedPage(
    val books: List<FeedBook>,
    /** The feed's `next` link as written in it, usually relative to the feed's address; null on the last page. */
    val next: String?,
)

/**
 * Reads the OPDS 1 (Atom) feeds of Project Gutenberg's catalogue, such as the results of
 * `https://www.gutenberg.org/ebooks/search.opds/?query=…`.
 *
 * Every `entry` whose `subsection` link or `id` points to a book's own feed (`/ebooks/{number}.opds`) becomes a
 * [FeedBook]: the title from its `title`, the author from its `author`'s `name` or, as search results give it, from a
 * plain-text `content`. Entries that lead elsewhere (authors, subjects, sort orders), repeated books, entries without
 * a title and the entries after the first [MAX_BOOKS] are left out. Text is cleaned like book text: whitespace
 * collapsed, invisible characters removed and accents composed; titles and authors are cut to [MetadataLimits].
 *
 * A feed comes from the network and is untrusted: it is parsed by the same parser as EPUB content, which loads no
 * DTD or external entity and expands no declared entity (SR-3). [parse] never throws.
 */
public object GutenbergFeedParser {
    /** The most books read from one feed; Gutenberg sends 25 per page. */
    public const val MAX_BOOKS: Int = 100

    /** A book's own feed, `/ebooks/55752.opds`, absolute or relative. */
    private val BOOK_FEED = Regex("/ebooks/(\\d{1,7})\\.opds$")

    /** The page in [xml], the text of a feed, or null when it is not an Atom feed. */
    public fun parse(xml: String): FeedPage? {
        val feed =
            SafeXml
                .parseText(xml)
                .children()
                .firstOrNull()
                ?.takeIf { it.localName == "feed" }
        return feed?.let {
            FeedPage(
                books =
                    it
                        .childrenNamed("entry")
                        .asSequence()
                        .mapNotNull(::book)
                        .distinctBy(FeedBook::id)
                        .take(MAX_BOOKS)
                        .toList(),
                next = it.links("next").firstNotNullOfOrNull { link -> link.attr("href").trim().ifEmpty { null } },
            )
        }
    }

    private fun book(entry: Element): FeedBook? {
        val id = bookId(entry)
        val title = text(entry.childrenNamed("title").firstOrNull())?.let(MetadataLimits::title)
        return if (id == null ||
            title == null
        ) {
            null
        } else {
            FeedBook(id, title, author(entry)?.let(MetadataLimits::author))
        }
    }

    /** The ebook number of the entry's `subsection` link, else of its `id`. */
    private fun bookId(entry: Element): Int? {
        val targets = entry.links("subsection").map { it.attr("href") } + entry.childrenNamed("id").map { it.text() }
        return targets
            .firstNotNullOfOrNull {
                BOOK_FEED
                    .find(it.trim())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }?.takeIf { it > 0 }
    }

    private fun author(entry: Element): String? =
        entry.childrenNamed("author").firstNotNullOfOrNull { text(it.childrenNamed("name").firstOrNull()) }
            ?: text(entry.childrenNamed("content").firstOrNull { it.attr("type").ifEmpty { "text" } == "text" })

    private fun Element.links(relation: String): List<Element> =
        childrenNamed("link").filter { relation in it.attributeTokens("rel") }

    private fun text(element: Element?): String? = element?.let { TextCleaner.clean(it.text()) }?.ifEmpty { null }
}
