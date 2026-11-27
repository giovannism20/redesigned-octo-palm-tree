package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.NormalizedBook

/**
 * Turns the layout of a document, as PDF extraction or OCR produces it, into a [NormalizedBook].
 *
 * 1. Line text is cleaned like any chapter text (whitespace, invisible characters, accents) and empty lines dropped.
 * 2. Running headers, running footers and page numbers are removed (rules on [RunningLines]).
 * 3. A document where most pages have no text left is reported as scanned ([ScannedDocumentDetector]).
 * 4. Paragraphs are rebuilt across pages from gaps, indents, short lines, dialogue dashes and font sizes, joining
 *    words broken by hyphenation while keeping the hyphen of Portuguese enclisis such as "fazê-lo" (rules on
 *    [ParagraphRules], [LineJoiner] and [Enclisis]).
 * 5. Chapters come from the outline, else from headings, else from page ranges (rules on [ChapterSplitter]).
 *
 * The rules assume one column of text per page. Instances keep no state and can be shared across threads.
 */
public class LayoutBookBuilder {
    /**
     * Builds the book described by [metadata] from its [pages], in document order, and its [outline]. Never throws
     * for unusual input: a document without readable text is reported as a [LayoutResult.Failure].
     */
    public fun build(
        metadata: BookMetadata,
        pages: List<PageLayout>,
        outline: List<OutlineEntry> = emptyList(),
    ): LayoutResult {
        val textPages = RunningLines.remove(pages.sortedBy(PageLayout::index).map(PageLayout::toTextPage))
        if (ScannedDocumentDetector.isScannedText(textPages)) return LayoutResult.Failure(LayoutError.ScannedDocument)
        val lines = textPages.flatMap(TextPage::lines)
        val chapters =
            if (lines.isEmpty()) {
                emptyList()
            } else {
                val paragraphs = ParagraphBuilder(lines, LayoutMetrics.measure(textPages))
                ChapterSplitter(lines, pages.size, paragraphs).split(outline)
            }
        return if (chapters.isEmpty()) {
            LayoutResult.Failure(LayoutError.NoReadableText)
        } else {
            LayoutResult.Success(NormalizedBook(metadata, chapters))
        }
    }
}

/** Outcome of [LayoutBookBuilder.build]: a book, or the reason there is none. */
public sealed interface LayoutResult {
    /** The document had readable text and became [book]. */
    public data class Success(
        val book: NormalizedBook,
    ) : LayoutResult

    /** The document could not become a book; [error] says why. */
    public data class Failure(
        val error: LayoutError,
    ) : LayoutResult
}

/** Why a document's layout could not become a book. Each case is meant to map to one user-facing message. */
public sealed interface LayoutError {
    /** Most pages are pictures without a text layer, so the book needs OCR (see [ScannedDocumentDetector]). */
    public data object ScannedDocument : LayoutError

    /** There is no text to read aloud, for example in an empty document or one of only page numbers. */
    public data object NoReadableText : LayoutError
}
