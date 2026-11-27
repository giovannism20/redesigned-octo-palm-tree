package com.giovannimartins.rreader.textkit.epub

import org.jsoup.nodes.Element

/** A table-of-contents entry: what it points to, its label and how deep it is nested. */
internal data class TocEntry(
    /** The label, or null when it is blank. */
    val title: String?,
    /** The archive entry it points to, without fragment. */
    val path: String,
    /** The fragment identifier it points to inside [path], percent-decoded, or null for the whole document. */
    val fragment: String? = null,
    /** 1 for a top-level entry, 2 for an entry nested in it, and so on. */
    val depth: Int = 1,
)

/** Reads a table of contents into a flat list of entries in document order, nested entries included. */
internal object TableOfContents {
    /**
     * The links of the EPUB 3 navigation document's `nav` whose `epub:type` includes `toc`. A link's depth is the
     * number of list items it sits in.
     */
    fun fromNavigationDocument(
        archive: EpubArchive,
        path: String,
        budget: ParseBudget = ParseBudget.UNLIMITED,
    ): List<TocEntry> {
        val toc =
            archive
                .read(path)
                ?.let { SafeXml.parse(it, path) }
                ?.elementsNamed("nav")
                ?.firstOrNull { "toc" in it.attributeTokens("epub:type") }
        return toc?.elementsNamed("a").orEmpty().mapNotNull { link ->
            budget.check()
            val depth = link.ancestorsUpTo(toc).count { it.localName == "li" }
            entry(path, link.attr("href"), ContentExtractor.inlineText(link), depth)
        }
    }

    /**
     * The `navPoint` elements of an EPUB 2 NCX `navMap`. A top-level point has depth 1, a point nested in it depth 2.
     */
    fun fromNcx(
        archive: EpubArchive,
        path: String,
        budget: ParseBudget = ParseBudget.UNLIMITED,
    ): List<TocEntry> {
        val navMap =
            archive
                .read(path)
                ?.let { SafeXml.parse(it, path) }
                ?.elementsNamed("navmap")
                ?.firstOrNull()
        return navMap?.elementsNamed("navpoint").orEmpty().mapNotNull { point ->
            budget.check()
            val label =
                point
                    .childrenNamed("navlabel")
                    .firstOrNull()
                    ?.childrenNamed("text")
                    ?.firstOrNull()
            val source =
                point
                    .childrenNamed("content")
                    .firstOrNull()
                    ?.attr("src")
                    .orEmpty()
            val depth = 1 + point.ancestorsUpTo(navMap).count { it.localName == "navpoint" }
            entry(path, source, label?.let(ContentExtractor::inlineText).orEmpty(), depth)
        }
    }

    private fun entry(
        documentPath: String,
        href: String,
        title: String,
        depth: Int,
    ): TocEntry? =
        ArchivePaths.resolve(ArchivePaths.parentOf(documentPath), href)?.let { path ->
            TocEntry(title.ifEmpty { null }, path, ArchivePaths.fragmentOf(href), depth.coerceAtLeast(1))
        }

    /** The ancestors of this element below [root], nearest first. */
    private fun Element.ancestorsUpTo(root: Element?): List<Element> = parents().takeWhile { it !== root }
}
