package com.giovannimartins.rreader.textkit.layout

/**
 * Recognizes scanned documents: pages that are pictures of text, with no text layer to read aloud. R Reader explains
 * that instead of importing an empty book, until on-device OCR arrives (roadmap Phase 4).
 *
 * A page has no usable text when, once running headers, footers and page numbers are left out (see
 * [LayoutBookBuilder]), it has fewer than [MIN_PAGE_CHARACTERS] letters and digits; scanning apps that stamp a small
 * caption on every page therefore still count as scans. A document is scanned when more than half of its pages have
 * no usable text. A document without pages is not scanned: it is simply empty.
 */
public object ScannedDocumentDetector {
    /** Letters and digits a page needs, outside its margins' running lines, to count as having text. */
    public const val MIN_PAGE_CHARACTERS: Int = 20

    /** Whether most [pages] lack a usable text layer. */
    public fun isScanned(pages: List<PageLayout>): Boolean =
        isScannedText(RunningLines.remove(pages.map(PageLayout::toTextPage)))

    /** [isScanned] for pages already cleaned and stripped of their running lines. */
    internal fun isScannedText(pages: List<TextPage>): Boolean {
        val textless = pages.count { page -> page.lines.sumOf(::characters) < MIN_PAGE_CHARACTERS }
        return pages.isNotEmpty() && textless * 2 > pages.size
    }

    private fun characters(line: TextLine): Int = line.text.count(Char::isLetterOrDigit)
}
