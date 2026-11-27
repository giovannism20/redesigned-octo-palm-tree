package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.gutenberg.GutenbergBoilerplate
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.giovannimartins.rreader.textkit.model.NormalizedBook

/** Reads one opened archive into a [NormalizedBook], following the rules described on [EpubParser]. */
internal class EpubBookReader(
    private val archive: EpubArchive,
) {
    fun read(fallbackTitle: String): NormalizedBook {
        if (DrmDetector.isProtected(archive)) abortParse(EpubError.DrmProtected)
        val packageDocument = PackageDocument.read(archive, OcfContainer.packagePath(archive))
        val documents = packageDocument.readingOrder.mapNotNull(::readSpineDocument)
        val chapters =
            GutenbergBoilerplate.strip(ChapterAssembler.assemble(documents, tablesOfContents(packageDocument)))
        if (chapters.isEmpty()) abortParse(EpubError.NoReadableText)
        return NormalizedBook(packageDocument.metadata(fallbackTitle), chapters, readCover(packageDocument))
    }

    /** Spine documents missing from the archive are skipped; sloppy books often list files they do not ship. */
    private fun readSpineDocument(path: String): SpineDocument? =
        archive.read(path)?.let { bytes -> SpineDocument(path, ContentExtractor.extract(SafeXml.parse(bytes, path))) }

    /** The EPUB 3 navigation document, then the EPUB 2 NCX; the NCX is only read when the first one does not fit. */
    private fun tablesOfContents(packageDocument: PackageDocument): Sequence<List<TocEntry>> =
        sequence {
            packageDocument.navigationDocument?.let { yield(TableOfContents.fromNavigationDocument(archive, it)) }
            packageDocument.ncx?.let { yield(TableOfContents.fromNcx(archive, it)) }
        }

    private fun readCover(packageDocument: PackageDocument): CoverImage? =
        packageDocument.coverImage?.let { cover ->
            archive.read(cover.path)?.takeIf { it.isNotEmpty() }?.let { CoverImage(cover.mediaType, it) }
        }
}
