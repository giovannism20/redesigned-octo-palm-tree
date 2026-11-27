package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.NormalizedBook
import com.google.common.truth.Truth.assertWithMessage
import java.io.File

/** The title [parseBook] and [parseError] pass as the fallback, as an importer would pass the file name. */
internal const val FALLBACK_TITLE = "Nome do arquivo"

private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

/** A complete XHTML content document around [body]. */
internal fun xhtml(body: String): String =
    listOf(
        XML_DECLARATION,
        "<!DOCTYPE html>",
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"pt-BR\">",
        "<head><title>Documento</title></head>",
        "<body>$body</body>",
        "</html>",
    ).joinToString("\n")

/** A manifest `item` element. */
internal fun manifestItem(
    id: String,
    href: String,
    mediaType: String,
    properties: String? = null,
): String {
    val propertiesAttribute = properties?.let { " properties=\"$it\"" }.orEmpty()
    return "<item id=\"$id\" href=\"$href\" media-type=\"$mediaType\"$propertiesAttribute/>"
}

/** An EPUB 2 NCX whose `navMap` holds [navPoints]. */
internal fun ncx(vararg navPoints: String): String =
    listOf(
        XML_DECLARATION,
        "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">",
        "<head/><docTitle><text>Livro de Teste</text></docTitle>",
        "<navMap>${navPoints.joinToString("")}</navMap>",
        "</ncx>",
    ).joinToString("\n")

/** An NCX `navPoint` labelled [title] that points to [source] and contains the nested [children]. */
internal fun navPoint(
    title: String,
    source: String,
    vararg children: String,
): String =
    "<navPoint><navLabel><text>$title</text></navLabel><content src=\"$source\"/>" +
        "${children.joinToString("")}</navPoint>"

/** An EPUB 3 navigation document whose `toc` nav lists [items] ([navItem]). */
internal fun navigation(vararg items: String): String =
    xhtml("<nav epub:type=\"toc\" id=\"toc\"><ol>${items.joinToString("")}</ol></nav>")

/** A list item of a navigation document linking [title] to [href] and holding the nested [children]. */
internal fun navItem(
    title: String,
    href: String,
    vararg children: String,
): String {
    val nested = if (children.isEmpty()) "" else "<ol>${children.joinToString("")}</ol>"
    return "<li><a href=\"$href\">$title</a>$nested</li>"
}

/** `p` elements of synthetic prose about [subject], [characters] characters or a little more in all. */
internal fun prose(
    subject: String,
    characters: Int = 400,
): String {
    val paragraph = "O relato sobre $subject segue devagar, frase após frase, sem pressa nenhuma de acabar. ".repeat(3)
    val count = (characters + paragraph.length - 1) / paragraph.length
    return "<p>${paragraph.trim()}</p>".repeat(count.coerceAtLeast(1))
}

/** Parses [file] and fails the test, showing the error, unless it became a book. */
internal fun parseBook(
    file: File,
    parser: EpubParser = EpubParser(),
): NormalizedBook {
    val result = parser.parse(file, FALLBACK_TITLE)
    assertWithMessage("parse result").that(result).isInstanceOf(EpubResult.Success::class.java)
    return (result as EpubResult.Success).book
}

/** Parses [file] and fails the test, showing the book, unless parsing failed. */
internal fun parseError(
    file: File,
    parser: EpubParser = EpubParser(),
): EpubError {
    val result = parser.parse(file, FALLBACK_TITLE)
    assertWithMessage("parse result").that(result).isInstanceOf(EpubResult.Failure::class.java)
    return (result as EpubResult.Failure).error
}

/**
 * Builds a small EPUB 3 (or, with [epub3] false, EPUB 2) archive from synthetic text. The defaults form a valid book;
 * each test overrides, adds or removes parts to exercise one behaviour. Metadata values and document bodies are raw
 * XML, so tests can use markup and entities.
 */
internal class EpubFixture(
    private val epub3: Boolean = true,
) {
    var packagePath = "OEBPS/content.opf"
    var title: String? = "Livro de Teste"
    val creators = mutableListOf("Autora Fictícia")
    var language: String? = "pt-BR"
    var description: String? = null
    val extraMetadata = mutableListOf<String>()
    val extraManifest = mutableListOf<String>()
    val extraSpine = mutableListOf<String>()
    private val documents = mutableListOf<FixtureDocument>()
    private val overrides = linkedMapOf<String, ByteArray>()
    private val removed = mutableSetOf<String>()

    /**
     * Adds a content document stored as [fileName] next to the package document and listed in the manifest as [href].
     * A [tocTitle] adds a table-of-contents entry pointing to [tocHref].
     */
    fun document(
        fileName: String,
        body: String,
        tocTitle: String? = null,
        linear: Boolean = true,
        href: String = fileName,
        tocHref: String = href,
    ) {
        documents += FixtureDocument("doc${documents.size + 1}", fileName, href, body, tocTitle, tocHref, linear)
    }

    /** Adds the archive entry [name], replacing the generated entry of the same name. */
    fun entry(
        name: String,
        content: String,
    ) = entry(name, content.toByteArray())

    fun entry(
        name: String,
        content: ByteArray,
    ) {
        overrides[name] = content
    }

    /** Leaves the archive entry [name] out. */
    fun remove(name: String) {
        removed += name
    }

    /** Replaces the generated EPUB 3 table of contents with one that lists [items] ([navItem]). */
    fun tableOfContents(vararg items: String) = entry(pathOf(NAV_FILE), navigation(*items))

    /** The archive entry name of [fileName] stored next to the package document. */
    fun pathOf(fileName: String): String {
        val directory = ArchivePaths.parentOf(packagePath)
        return if (directory.isEmpty()) fileName else "$directory/$fileName"
    }

    fun writeTo(file: File): File {
        val entries = linkedMapOf<String, ByteArray>()
        entries["mimetype"] = "application/epub+zip".toByteArray()
        entries["META-INF/container.xml"] = containerXml().toByteArray()
        entries[packagePath] = packageXml().toByteArray()
        entries[pathOf(if (epub3) NAV_FILE else NCX_FILE)] = (if (epub3) navigationXhtml() else ncxXml()).toByteArray()
        documents.forEach { entries[pathOf(it.fileName)] = xhtml(it.body).toByteArray() }
        entries.putAll(overrides)
        return writeZip(file, entries.filterKeys { it !in removed }.toList())
    }

    private fun containerXml(): String =
        listOf(
            XML_DECLARATION,
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>",
            "<rootfile full-path=\"$packagePath\" media-type=\"application/oebps-package+xml\"/>",
            "</rootfiles></container>",
        ).joinToString("\n")

    /** The generated package document, for tests that rewrite it. */
    fun packageXml(): String {
        val tocItem =
            if (epub3) {
                "<item id=\"nav\" href=\"$NAV_FILE\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
            } else {
                "<item id=\"ncx\" href=\"$NCX_FILE\" media-type=\"application/x-dtbncx+xml\"/>"
            }
        return buildList {
            add(XML_DECLARATION)
            add("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"${if (epub3) "3.0" else "2.0"}\">")
            add("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">")
            add("<dc:identifier id=\"uid\">livro-de-teste</dc:identifier>")
            title?.let { add("<dc:title>$it</dc:title>") }
            creators.forEach { add("<dc:creator>$it</dc:creator>") }
            language?.let { add("<dc:language>$it</dc:language>") }
            description?.let { add("<dc:description>$it</dc:description>") }
            addAll(extraMetadata)
            add("</metadata>")
            add("<manifest>")
            add(tocItem)
            documents.forEach { add(manifestItem(it.id, it.href, "application/xhtml+xml")) }
            addAll(extraManifest)
            add("</manifest>")
            add(if (epub3) "<spine>" else "<spine toc=\"ncx\">")
            documents.forEach { add("<itemref idref=\"${it.id}\"${if (it.linear) "" else " linear=\"no\""}/>") }
            addAll(extraSpine)
            add("</spine>")
            add("</package>")
        }.joinToString("\n")
    }

    private fun navigationXhtml(): String {
        val links = tocDocuments().joinToString("") { "<li><a href=\"${it.tocHref}\">${it.tocTitle}</a></li>" }
        return xhtml("<nav epub:type=\"toc\" id=\"toc\"><h1>Sumário</h1><ol>$links</ol></nav>")
    }

    private fun ncxXml(): String =
        ncx(*tocDocuments().map { navPoint(it.tocTitle.orEmpty(), it.tocHref) }.toTypedArray())

    private fun tocDocuments(): List<FixtureDocument> = documents.filter { it.tocTitle != null }

    private class FixtureDocument(
        val id: String,
        val fileName: String,
        val href: String,
        val body: String,
        val tocTitle: String?,
        val tocHref: String,
        val linear: Boolean,
    )

    companion object {
        const val NAV_FILE = "nav.xhtml"
        const val NCX_FILE = "toc.ncx"
    }
}
