package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import org.jsoup.nodes.Element

/** A manifest resource whose href points into the archive. */
internal data class ManifestItem(
    val id: String,
    /** Archive entry name. */
    val path: String,
    /** Lower-case media type without parameters, for example `application/xhtml+xml`. */
    val mediaType: String,
    /** EPUB 3 `properties` tokens, for example `nav` or `cover-image`. */
    val properties: Set<String>,
)

/**
 * The package document (OPF): metadata, manifest and spine. Elements are matched by local name, so prefixed forms
 * such as `opf:item` work too. Manifest items whose href does not point into the archive are left out.
 */
internal class PackageDocument private constructor(
    private val metadataElement: Element?,
    private val items: List<ManifestItem>,
    private val spine: Element,
) {
    // Items without an id cannot be referenced; keeping them would let a missing `idref` or `toc` match them.
    private val itemsById: Map<String, ManifestItem> =
        items.filter { it.id.isNotEmpty() }.distinctBy { it.id }.associateBy { it.id }

    /** Linear (X)HTML content documents in spine order, each listed once. */
    val readingOrder: List<String>
        get() =
            spine
                .elementsNamed("itemref")
                .filterNot { it.attr("linear").trim().equals("no", ignoreCase = true) }
                .mapNotNull { itemsById[it.attr("idref").trim()] }
                .filter { it.isContentDocument() }
                .map { it.path }
                .distinct()

    /** The EPUB 3 navigation document, if the manifest declares one. */
    val navigationDocument: String?
        get() = items.firstOrNull { NAV_PROPERTY in it.properties }?.path

    /** The EPUB 2 NCX: the item named by the spine's `toc` attribute, else any item with the NCX media type. */
    val ncx: String?
        get() = (itemsById[spine.attr("toc").trim()] ?: items.firstOrNull { it.mediaType == NCX_MEDIA_TYPE })?.path

    /** The EPUB 3 `cover-image` item, else the item named by EPUB 2 `<meta name="cover">`; images only. */
    val coverImage: ManifestItem?
        get() {
            val epub3Covers = items.filter { COVER_IMAGE_PROPERTY in it.properties }
            val epub2Cover = metadataElements("meta").firstOrNull { it.attr("name").trim() == "cover" }
            val candidates = epub3Covers + listOfNotNull(epub2Cover?.let { itemsById[it.attr("content").trim()] })
            return candidates.firstOrNull { it.mediaType.startsWith(IMAGE_MEDIA_TYPE_PREFIX) }
        }

    /**
     * The book's metadata, within [MetadataLimits]; [fallbackTitle] replaces a missing or blank first `dc:title`. A
     * language that is not a BCP 47 tag is left out.
     */
    fun metadata(fallbackTitle: String): BookMetadata {
        val title = metadataElements("title").firstOrNull()?.let(ContentExtractor::inlineText).orEmpty()
        return BookMetadata(
            title = MetadataLimits.title(title.ifEmpty { fallbackTitle }),
            authors =
                metadataTexts("creator")
                    .distinct()
                    .take(MetadataLimits.MAX_AUTHORS)
                    .map(MetadataLimits::author)
                    .toList(),
            language = metadataTexts("language").firstOrNull()?.replace('_', '-')?.let(MetadataLimits::language),
            description =
                metadataElements("description")
                    .firstOrNull()
                    ?.let(::plainDescription)
                    ?.ifEmpty { null }
                    ?.let(MetadataLimits::description),
        )
    }

    private fun metadataElements(name: String): List<Element> = metadataElement?.elementsNamed(name).orEmpty()

    private fun metadataTexts(name: String): Sequence<String> =
        metadataElements(name).asSequence().map(ContentExtractor::inlineText).filter { it.isNotEmpty() }

    /** Descriptions often carry HTML, escaped as text or as child elements; both become plain paragraphs. */
    private fun plainDescription(description: Element): String {
        val markup = if (description.childrenSize() == 0) description.wholeText() else description.html()
        val paragraphs = ContentExtractor.extract(SafeXml.parseHtmlFragment(markup)).paragraphs
        return paragraphs.joinToString(Chapter.PARAGRAPH_SEPARATOR)
    }

    companion object {
        private const val NAV_PROPERTY = "nav"
        private const val COVER_IMAGE_PROPERTY = "cover-image"
        private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"
        private const val IMAGE_MEDIA_TYPE_PREFIX = "image/"
        private val CONTENT_MEDIA_TYPES = setOf("application/xhtml+xml", "text/html", "text/x-oeb1-document")
        private val CONTENT_EXTENSIONS = setOf("xhtml", "html", "htm")

        /** Reads the package document at [path]; it must have a manifest and a spine. */
        fun read(
            archive: EpubArchive,
            path: String,
        ): PackageDocument {
            val bytes = archive.read(path) ?: abortParse(EpubError.MissingPackageDocument)
            val document = SafeXml.parse(bytes, path)
            val manifest = document.elementsNamed("manifest").firstOrNull()
            val spine = document.elementsNamed("spine").firstOrNull()
            if (manifest == null || spine == null) abortParse(EpubError.MalformedDocument(path))
            val baseDirectory = ArchivePaths.parentOf(path)
            val items = manifest.elementsNamed("item").mapNotNull { it.toManifestItem(baseDirectory) }
            return PackageDocument(document.elementsNamed("metadata").firstOrNull(), items, spine)
        }

        private fun Element.toManifestItem(baseDirectory: String): ManifestItem? =
            ArchivePaths.resolve(baseDirectory, attr("href"))?.let { path ->
                ManifestItem(
                    id = attr("id").trim(),
                    path = path,
                    mediaType = attr("media-type").substringBefore(';').trim().lowercase(),
                    properties = attributeTokens("properties"),
                )
            }

        private fun ManifestItem.isContentDocument(): Boolean =
            mediaType in CONTENT_MEDIA_TYPES || path.substringAfterLast('.', "").lowercase() in CONTENT_EXTENSIONS
    }
}
