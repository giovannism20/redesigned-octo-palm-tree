package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.TextCleaner
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeFilter.FilterResult

/** The readable text of one content document, with the landmarks that chapter detection uses. */
internal class ContentDocument(
    /** Cleaned, non-empty paragraphs in reading order. */
    val paragraphs: List<String>,
    /** The `h1`–`h6` elements that have text, in reading order. */
    val headings: List<HeadingBlock> = emptyList(),
    /**
     * Where each element `id`, or the `name` of an `a` element, points: the index of the first paragraph that starts
     * at or after the element. An element that comes after text of its own paragraph, such as an empty `a` at the end
     * of a sentence, points to the next paragraph, and one after the last paragraph points to [paragraphs]`.size`.
     * Elements that are not read aloud point to the text after them. The first element with a name wins.
     */
    val anchors: Map<String, Int> = emptyMap(),
    /**
     * The first paragraph of each chapter division that has text, in order: a block element whose `epub:type` or
     * `class` includes `chapter`, as EPUB 3 sections and Project Gutenberg's `<div class="chapter">` are marked.
     */
    val chapterDivisions: List<Int> = emptyList(),
) {
    /** The text of the first `h1`–`h3` heading that has text, or null. */
    val heading: String? get() = headings.firstOrNull { it.isTitleLevel }?.text
}

/** A heading element (`h1`–`h6`) that has text. */
internal class HeadingBlock(
    /** 1 for `h1` up to 6 for `h6`. */
    val level: Int,
    /** The first paragraph the heading's text takes. */
    val firstParagraph: Int,
    /** The paragraph after the heading's last one. */
    val endParagraph: Int,
    /** The heading's text on a single line. */
    val text: String,
) {
    /** Whether this is an `h1`–`h3`: the headings that title a chapter which has no title of its own. */
    val isTitleLevel: Boolean get() = level <= TITLE_HEADING_LEVELS
}

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
    /** Extracts the paragraphs of the tree under [root] and where its headings, anchors and chapter divisions are. */
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
private val HEADING_LEVELS = (1..6).associateBy { "h$it" }
private const val TITLE_HEADING_LEVELS = 3
private const val CHAPTER = "chapter"
private val NOTE_SEMANTICS =
    setOf("noteref", "footnote", "endnote", "rearnote", "doc-noteref", "doc-footnote", "doc-endnote")

/**
 * Walks a tree once, cutting paragraphs at block boundaries, skipping content that is not read aloud and noting the
 * headings, anchors and chapter divisions it passes.
 */
private class ParagraphCollector : NodeFilter {
    private val paragraphs = mutableListOf<String>()
    private val pending = StringBuilder()
    private val headings = mutableListOf<HeadingBlock>()
    private val anchors = HashMap<String, Int>()
    private val divisions = mutableListOf<Int>()
    private val openDivisions = ArrayDeque<Int>()
    private var openHeading: Element? = null
    private var headingStart = 0

    /** Whether [pending] holds text that cleaning keeps; tracked as text arrives so anchors are placed in O(1). */
    private var pendingVisible = false

    override fun head(
        node: Node,
        depth: Int,
    ): FilterResult =
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                pending.append(text)
                pendingVisible = pendingVisible || TextCleaner.hasVisibleText(text)
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
            if (node === openHeading) closeHeading(node)
            if (node.isChapterDivision()) closeDivision()
        }
        return FilterResult.CONTINUE
    }

    fun finish(): ContentDocument {
        endParagraph()
        return ContentDocument(paragraphs.toList(), headings.toList(), anchors, divisions.sorted().distinct())
    }

    private fun enter(element: Element): FilterResult {
        val name = element.localName
        if (element.isNotReadAloud(name)) {
            markAnchor(element)
            return FilterResult.SKIP_ENTIRELY
        }
        if (name == "br") pending.append(' ')
        if (name in BLOCKS) {
            endParagraph()
            if (name in HEADING_LEVELS && openHeading == null) {
                openHeading = element
                headingStart = paragraphs.size
            }
            if (element.isChapterDivision()) openDivisions.addLast(paragraphs.size)
        }
        markAnchor(element)
        return FilterResult.CONTINUE
    }

    /** Notes the paragraph that [element]'s `id`, and an `a` element's `name`, point to ([ContentDocument.anchors]). */
    private fun markAnchor(element: Element) {
        val id = element.id().trim()
        val name = if (element.localName == "a") element.attr("name").trim() else ""
        if (id.isEmpty() && name.isEmpty()) return
        val paragraph = if (pendingVisible) paragraphs.size + 1 else paragraphs.size
        listOf(id, name).filter(String::isNotEmpty).forEach { anchors.putIfAbsent(it, paragraph) }
    }

    private fun closeHeading(heading: Element) {
        if (paragraphs.size > headingStart) {
            val text = paragraphs.subList(headingStart, paragraphs.size).joinToString(" ")
            headings += HeadingBlock(HEADING_LEVELS.getValue(heading.localName), headingStart, paragraphs.size, text)
        }
        openHeading = null
    }

    private fun closeDivision() {
        val start = openDivisions.removeLastOrNull()
        if (start != null && paragraphs.size > start) divisions += start
    }

    private fun endParagraph() {
        val text = TextCleaner.clean(pending)
        pending.setLength(0)
        pendingVisible = false
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

/** A block element marked as a chapter: `epub:type="chapter"` (EPUB 3) or `class="chapter"` (Project Gutenberg). */
private fun Element.isChapterDivision(): Boolean =
    localName in BLOCKS && (CHAPTER in attributeTokens("epub:type") || CHAPTER in attributeTokens("class"))
