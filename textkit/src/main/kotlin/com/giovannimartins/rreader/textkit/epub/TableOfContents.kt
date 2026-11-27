package com.giovannimartins.rreader.textkit.epub

/** A table-of-contents entry: its label (null when blank) and the archive entry it points to, without fragment. */
internal data class TocEntry(
    val title: String?,
    val path: String,
)

/** Reads a table of contents into a flat list of entries in document order, nested entries included. */
internal object TableOfContents {
    /** The links of the EPUB 3 navigation document's `nav` whose `epub:type` includes `toc`. */
    fun fromNavigationDocument(
        archive: EpubArchive,
        path: String,
    ): List<TocEntry> {
        val toc =
            archive
                .read(path)
                ?.let { SafeXml.parse(it, path) }
                ?.elementsNamed("nav")
                ?.firstOrNull { "toc" in it.attributeTokens("epub:type") }
        return toc?.elementsNamed("a").orEmpty().mapNotNull { link ->
            entry(path, link.attr("href"), ContentExtractor.inlineText(link))
        }
    }

    /** The `navPoint` elements of an EPUB 2 NCX `navMap`. */
    fun fromNcx(
        archive: EpubArchive,
        path: String,
    ): List<TocEntry> {
        val navMap =
            archive
                .read(path)
                ?.let { SafeXml.parse(it, path) }
                ?.elementsNamed("navmap")
                ?.firstOrNull()
        return navMap?.elementsNamed("navpoint").orEmpty().mapNotNull { point ->
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
            entry(path, source, label?.let(ContentExtractor::inlineText).orEmpty())
        }
    }

    private fun entry(
        documentPath: String,
        href: String,
        title: String,
    ): TocEntry? =
        ArchivePaths.resolve(ArchivePaths.parentOf(documentPath), href)?.let { TocEntry(title.ifEmpty { null }, it) }
}
