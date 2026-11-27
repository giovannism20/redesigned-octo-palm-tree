package com.giovannimartins.rreader.textkit.layout

/**
 * Recognizes pages without a usable text layer, such as scanned pages, which are pictures of text.
 *
 * A page has no usable text when, once running headers, footers and page numbers are left out (see
 * [LayoutBookBuilder]), it has fewer than [MIN_PAGE_CHARACTERS] letters and digits; scanning apps that stamp a small
 * caption on every page therefore still count as scans. [pagesWithoutText] lists such pages, which R Reader reads
 * with on-device OCR. A document is scanned when more than half of its pages have no usable text: once its pages went
 * through OCR, that means no readable text could be recognized. A document without pages is not scanned: it is
 * simply empty.
 */
public object ScannedDocumentDetector {
    /** Letters and digits a page needs, outside its margins' running lines, to count as having text. */
    public const val MIN_PAGE_CHARACTERS: Int = 20

    /** Whether most [pages] lack a usable text layer. */
    public fun isScanned(pages: List<PageLayout>): Boolean = isScannedText(textPages(pages))

    /** The [PageLayout.index] of each of [pages] that lacks a usable text layer, in document order. */
    public fun pagesWithoutText(pages: List<PageLayout>): List<Int> =
        textPages(pages).filter(::lacksText).map(TextPage::index)

    /** [isScanned] for pages already cleaned and stripped of their running lines. */
    internal fun isScannedText(pages: List<TextPage>): Boolean {
        val textless = pages.count(::lacksText)
        return pages.isNotEmpty() && textless * 2 > pages.size
    }

    private fun textPages(pages: List<PageLayout>): List<TextPage> =
        RunningLines.remove(pages.sortedBy(PageLayout::index).map(PageLayout::toTextPage))

    private fun lacksText(page: TextPage): Boolean = page.lines.sumOf(::characters) < MIN_PAGE_CHARACTERS

    private fun characters(line: TextLine): Int = line.text.count(Char::isLetterOrDigit)
}
