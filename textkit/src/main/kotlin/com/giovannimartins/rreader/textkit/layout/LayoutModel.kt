package com.giovannimartins.rreader.textkit.layout

/**
 * One line of text as a PDF extractor or an OCR engine sees it: its text and where it sits on the page.
 *
 * Coordinates are in the page's own units (points for PDF text, pixels for OCR), with the origin at the top-left
 * corner of the page and y growing downwards, the same units as [PageLayout.width] and [PageLayout.height].
 */
public data class LayoutLine(
    /** The line's characters in reading order, as extracted (whitespace and invisible characters are cleaned later). */
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    /** The baseline for extracted PDF text; the bottom edge of the box for OCR. */
    val bottom: Float,
    /** Typical glyph size on the line, in page units; for OCR, the height of the line. */
    val fontSize: Float,
) {
    init {
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
        require(fontSize >= 0f) { "fontSize must not be negative" }
    }
}

/** One page of a document: its size and its lines in reading order. */
public data class PageLayout(
    /** Zero-based position of the page in the document. */
    val index: Int,
    val width: Float,
    val height: Float,
    val lines: List<LayoutLine>,
) {
    init {
        require(index >= 0) { "index must not be negative" }
        require(width >= 0f && height >= 0f) { "Page sizes must not be negative" }
    }
}

/**
 * An entry of a document's outline (the bookmarks of a PDF), which marks where a chapter starts.
 *
 * Entries that point outside the document are ignored.
 */
public data class OutlineEntry(
    val title: String,
    /** Zero-based index of the page the entry points to. */
    val pageIndex: Int,
    /** Where on that page the entry points, from the top edge in page units, or null for the whole page. */
    val top: Float? = null,
)
