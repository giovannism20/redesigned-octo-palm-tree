package com.giovannimartins.rreader.textkit.epub

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeFilter.FilterResult

/** The readable text of one content document. */
internal class ContentDocument(
    /** Cleaned, non-empty paragraphs in reading order. */
    val paragraphs: List<String>,
    /** The text of the first `h1`–`h3` heading that has text, or null. */
    val heading: String?,
)

/**
 * Extracts the text a listener should hear from (X)HTML.
 *
 * Block elements (`p`, `div`, headings, list items, quotes, `pre`, definition terms, captions, table cells and the
 * usual sectioning elements) separate paragraphs, so text placed directly inside a `div` becomes a paragraph of its
 * own; `br` becomes a space. Never spoken: `head`, `script`, `style`, `nav`, ruby annotations (`rt`, `rp`), footnote
 * references (`epub:type="noteref"` or `role="doc-noteref"`, and `sup` markers made only of digits or symbols or
 * holding a link) and footnote bodies (`epub:type` footnote, endnote or rearnote, or `role` doc-footnote or
 * doc-endnote). All text goes through [TextCleaner.clean].
 */
internal object ContentExtractor {
    /** Extracts the paragraphs and the first heading of the tree under [root]. */
    fun extract(root: Node): ContentDocument = ParagraphCollector().apply { traverse(root) }.finish()

    /** The readable text of [element] on a single line, as used for titles and labels. */
    fun inlineText(element: Element): String = extract(element).paragraphs.joinToString(" ")
}

private val BLOCKS =
    setOf(
        "address",
        "article",
        "aside",
        "blockquote",
        "body",
        "caption",
        "dd",
        "div",
        "dl",
        "dt",
        "figcaption",
        "figure",
        "footer",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "header",
        "hgroup",
        "hr",
        "li",
        "main",
        "ol",
        "p",
        "pre",
        "section",
        "table",
        "tbody",
        "td",
        "tfoot",
        "th",
        "thead",
        "tr",
        "ul",
    )
private val SKIPPED = setOf("head", "script", "style", "nav", "rt", "rp")
private val TITLE_HEADINGS = setOf("h1", "h2", "h3")
private val NOTE_SEMANTICS =
    setOf("noteref", "footnote", "endnote", "rearnote", "doc-noteref", "doc-footnote", "doc-endnote")

/** Walks a tree once, cutting paragraphs at block boundaries and skipping content that is not read aloud. */
private class ParagraphCollector : NodeFilter {
    private val paragraphs = mutableListOf<String>()
    private val pending = StringBuilder()
    private var heading: String? = null
    private var openHeading: Element? = null
    private var headingStart = 0

    override fun head(
        node: Node,
        depth: Int,
    ): FilterResult =
        when (node) {
            is TextNode -> {
                pending.append(node.wholeText)
                FilterResult.CONTINUE
            }

            is Element -> {
                enter(node)
            }

            else -> {
                FilterResult.CONTINUE
            }
        }

    override fun tail(
        node: Node,
        depth: Int,
    ): FilterResult {
        if (node is Element && node.localName in BLOCKS) {
            endParagraph()
            if (node === openHeading) closeHeading()
        }
        return FilterResult.CONTINUE
    }

    fun finish(): ContentDocument {
        endParagraph()
        return ContentDocument(paragraphs.toList(), heading)
    }

    private fun enter(element: Element): FilterResult {
        val name = element.localName
        if (element.isNotReadAloud(name)) return FilterResult.SKIP_ENTIRELY
        if (name == "br") pending.append(' ')
        if (name in BLOCKS) {
            endParagraph()
            if (name in TITLE_HEADINGS && heading == null && openHeading == null) {
                openHeading = element
                headingStart = paragraphs.size
            }
        }
        return FilterResult.CONTINUE
    }

    private fun closeHeading() {
        heading = paragraphs.subList(headingStart, paragraphs.size).joinToString(" ").ifEmpty { null }
        openHeading = null
    }

    private fun endParagraph() {
        val text = TextCleaner.clean(pending)
        pending.setLength(0)
        if (text.isNotEmpty()) paragraphs += text
    }
}

/** Metadata, scripts, navigation, ruby annotations and footnotes: content whose whole subtree is skipped. */
private fun Element.isNotReadAloud(name: String): Boolean =
    name in SKIPPED || hasNoteSemantics() || (name == "sup" && isNoteMarker())

private fun Element.hasNoteSemantics(): Boolean =
    attributeTokens("epub:type").any(NOTE_SEMANTICS::contains) || attributeTokens("role").any(NOTE_SEMANTICS::contains)

/** A `sup` footnote marker: digits or symbols only (`1`, `*`, `[2]`) or a link. Letters (`1<sup>o</sup>`) stay. */
private fun Element.isNoteMarker(): Boolean {
    val text = text()
    return elementsNamed("a").any { it.hasAttr("href") } || (text.isNotBlank() && text.none(Char::isLetter))
}
