package com.giovannimartins.rreader.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File

/**
 * Builds small PDFs with PdfBox-Android itself, using the standard 14 fonts, so tests need no binary fixtures.
 * Pages are A5 (420 x 595 points); positions are given from the top-left corner, as the extractor reports them.
 */
internal class PdfFixture(
    context: Context,
) {
    private val pages = mutableListOf<List<Text>>()
    private val shapes = mutableSetOf<Int>()
    private val bookmarks = mutableListOf<Bookmark>()
    var title: String? = null
    var author: String? = null
    var language: String? = null
    var userPassword: String? = null
    var ownerPassword: String? = null
    var rotation = 0

    init {
        PDFBoxResourceLoader.init(context)
    }

    /** Adds a page whose text [block] draws. */
    fun page(block: PageText.() -> Unit) {
        pages += PageText().apply(block).texts
    }

    /** Adds a page with a filled rectangle and no text, as a scan looks to a text extractor. */
    fun picturePage() {
        shapes += pages.size
        pages += emptyList<Text>()
    }

    /** A bookmark to page [pageIndex], at [top] points from the top edge or to the whole page; [children] nest. */
    fun bookmark(
        title: String,
        pageIndex: Int,
        top: Float? = null,
        children: List<Bookmark> = emptyList(),
    ): Bookmark = Bookmark(title, pageIndex, top, children).also(bookmarks::add)

    fun bytes(): ByteArray =
        File.createTempFile("fixture", ".pdf").let { file ->
            writeTo(file).readBytes().also {
                file.delete()
            }
        }

    fun writeTo(file: File): File {
        PDDocument().use { document ->
            pages.forEachIndexed { index, texts -> document.addPage(drawPage(document, texts, index in shapes)) }
            if (bookmarks.isNotEmpty()) {
                document.documentCatalog.documentOutline =
                    PDDocumentOutline().apply { bookmarks.filter { it.root }.forEach { addLast(it.toItem(document)) } }
            }
            document.documentInformation.title = title
            document.documentInformation.author = author
            language?.let { document.documentCatalog.language = it }
            userPassword?.let { user ->
                val policy = StandardProtectionPolicy(ownerPassword ?: user, user, AccessPermission())
                policy.permissions.setCanExtractContent(false)
                policy.encryptionKeyLength = KEY_LENGTH
                document.protect(policy)
            }
            document.save(file)
        }
        return file
    }

    private fun drawPage(
        document: PDDocument,
        texts: List<Text>,
        withShape: Boolean,
    ): PDPage {
        val page = PDPage(PDRectangle.A5).also { it.rotation = rotation }
        PDPageContentStream(document, page).use { stream ->
            if (withShape) {
                stream.addRect(MARGIN, MARGIN, page.mediaBox.width - 2 * MARGIN, page.mediaBox.height - 2 * MARGIN)
                stream.fill()
            }
            texts.forEach { text ->
                stream.beginText()
                stream.setFont(text.font, text.size)
                stream.newLineAtOffset(text.x, page.mediaBox.height - text.baseline)
                stream.showText(text.text)
                stream.endText()
            }
        }
        return page
    }

    private fun Bookmark.toItem(document: PDDocument): PDOutlineItem {
        val item = PDOutlineItem()
        item.title = title
        val target = document.getPage(pageIndex)
        item.destination =
            if (top == null) {
                PDPageFitDestination().apply { page = target }
            } else {
                val fromBottom = (target.mediaBox.height - top).toInt()
                PDPageXYZDestination().apply {
                    page = target
                    this.top = fromBottom
                }
            }
        children.forEach { item.addLast(it.toItem(document)) }
        return item
    }

    /** Text lines of one page, each at a baseline measured from the top of the page. */
    class PageText {
        val texts = mutableListOf<Text>()
        private var baseline = FIRST_BASELINE

        /** A line at the next baseline, [LEADING] points below the previous one. */
        fun line(
            text: String,
            x: Float = LEFT,
            size: Float = BODY_SIZE,
            font: PDFont = PDType1Font.TIMES_ROMAN,
        ) {
            texts += Text(text, x, baseline, size, font)
            baseline += LEADING * size / BODY_SIZE
        }

        /** A line at a fixed baseline that does not move the text cursor, for headers and footers. */
        fun at(
            baseline: Float,
            text: String,
            x: Float = LEFT,
            size: Float = MARGIN_SIZE,
        ) {
            texts += Text(text, x, baseline, size, PDType1Font.HELVETICA)
        }

        /** Leaves [lines] empty lines. */
        fun skip(lines: Int = 1) {
            baseline += LEADING * lines
        }
    }

    class Text(
        val text: String,
        val x: Float,
        val baseline: Float,
        val size: Float,
        val font: PDFont,
    )

    inner class Bookmark(
        val title: String,
        val pageIndex: Int,
        val top: Float?,
        val children: List<Bookmark>,
    ) {
        /** Children are listed in their parent; only the others hang from the outline itself. */
        val root: Boolean get() = bookmarks.none { this in it.children }
    }

    companion object {
        const val LEFT = 50f
        const val INDENT = 68f
        const val FIRST_BASELINE = 90f
        const val LEADING = 15f
        const val BODY_SIZE = 12f
        const val MARGIN_SIZE = 9f
        const val HEADER_BASELINE = 40f
        const val FOOTER_BASELINE = 565f
        private const val MARGIN = 30f
        private const val KEY_LENGTH = 128
    }
}
