package com.giovannimartins.rreader.textkit.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * The only way EPUB documents are parsed: container, package, NCX, navigation and content documents (SR-3).
 *
 * Jsoup's XML parser keeps a DOCTYPE as an inert node. It never loads external DTDs or entities and never expands
 * entities declared in an internal subset, so XXE and "billion laughs" payloads stay literal text; only the fixed set
 * of XML and HTML named character references is decoded. It is lenient with malformed markup, and the encoding comes
 * from a byte order mark, the XML declaration or a `meta` charset, defaulting to UTF-8.
 */
internal object SafeXml {
    /** Parses [bytes], read from the archive entry [path]. */
    fun parse(
        bytes: ByteArray,
        path: String,
    ): Document =
        try {
            Jsoup.parse(ByteArrayInputStream(bytes), null, "", Parser.xmlParser())
        } catch (e: IOException) {
            abortParse(EpubError.MalformedDocument(path), e)
        }
}

private val WHITESPACE = Regex("\\s+")

/** The element's name without its namespace prefix, in lower case: `dc:Title` becomes `title`. */
internal val Element.localName: String get() = normalName().substringAfterLast(':')

/** This element and its descendants whose [localName] is [name], in document order. */
internal fun Element.elementsNamed(name: String): List<Element> = allElements.filter { it.localName == name }

/** The direct children whose [localName] is [name], in document order. */
internal fun Element.childrenNamed(name: String): List<Element> = children().filter { it.localName == name }

/** The whitespace-separated tokens of [attribute], such as `epub:type` or `properties`; empty when it is absent. */
internal fun Element.attributeTokens(attribute: String): Set<String> {
    val value = attr(attribute)
    return if (value.isBlank()) emptySet() else value.trim().split(WHITESPACE).toSet()
}
