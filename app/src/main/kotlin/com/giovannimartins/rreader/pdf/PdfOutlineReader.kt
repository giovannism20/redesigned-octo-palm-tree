package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.textkit.layout.OutlineEntry
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitRectangleDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Reads a PDF's outline (bookmarks) in document order: every entry with its title, the index of the page it points to
 * and, on pages that are not rotated, the height its destination points at.
 *
 * Outlines come from untrusted files, so reading stops after visiting [MAX_ENTRIES] entries or going [MAX_DEPTH] levels
 * deep, and an entry already visited (an outline whose links loop) ends its branch. Entries whose destination is
 * missing, broken or in another file are skipped.
 */
internal object PdfOutlineReader {
    const val MAX_ENTRIES: Int = 5_000
    const val MAX_DEPTH: Int = 32

    fun read(document: PDDocument): List<OutlineEntry> {
        val first = document.documentCatalog.documentOutline?.firstChild ?: return emptyList()
        val walk = Walk(document)
        walk.siblings(first, depth = 1)
        return walk.entries
    }

    private class Walk(
        private val document: PDDocument,
    ) {
        val entries = mutableListOf<OutlineEntry>()
        private val seen = IdentityHashMap<COSDictionary, Unit>()
        private val pageIndexes =
            IdentityHashMap<COSDictionary, Int>().apply {
                document.pages.forEachIndexed { index, page -> put(page.cosObject, index) }
            }

        fun siblings(
            first: PDOutlineItem,
            depth: Int,
        ) {
            var item: PDOutlineItem? = first
            while (item != null && seen.size < MAX_ENTRIES && seen.put(item.cosObject, Unit) == null) {
                entryOf(item)?.let(entries::add)
                val child = item.firstChild
                if (child != null && depth < MAX_DEPTH) siblings(child, depth + 1)
                item = item.nextSibling
            }
        }

        private fun entryOf(item: PDOutlineItem): OutlineEntry? =
            try {
                destinationOf(item)?.let { destination ->
                    val page = destination.page
                    val index = page?.let { pageIndexes[it.cosObject] } ?: destination.pageNumber
                    if (index in 0 until document.numberOfPages) {
                        OutlineEntry(item.title.orEmpty(), index, topOf(destination, page ?: document.getPage(index)))
                    } else {
                        null
                    }
                }
            } catch (e: IOException) {
                Diagnostics.recordHandled("read PDF outline entry", e)
                null
            } catch (e: ClassCastException) {
                Diagnostics.recordHandled("read PDF outline entry", e)
                null
            }

        private fun destinationOf(item: PDOutlineItem): PDPageDestination? =
            when (val destination = item.destination ?: (item.action as? PDActionGoTo)?.destination) {
                is PDPageDestination -> destination
                is PDNamedDestination -> document.documentCatalog.findNamedDestinationPage(destination)
                else -> null
            }

        /** The destination's height from the top of an upright page, in the coordinates of the extracted lines. */
        private fun topOf(
            destination: PDPageDestination,
            page: PDPage,
        ): Float? {
            val top =
                when (destination) {
                    is PDPageXYZDestination -> destination.top
                    is PDPageFitWidthDestination -> destination.top
                    is PDPageFitRectangleDestination -> destination.top
                    else -> UNSET
                }
            val box = page.cropBox
            return if (top == UNSET ||
                page.rotation != 0
            ) {
                null
            } else {
                (box.upperRightY - top).coerceIn(0f, abs(box.height))
            }
        }
    }

    /** What PdfBox returns for a destination coordinate that is not set. */
    private const val UNSET = -1
}
