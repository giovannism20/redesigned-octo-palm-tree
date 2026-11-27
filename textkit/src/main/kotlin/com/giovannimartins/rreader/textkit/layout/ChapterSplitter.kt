package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.TextCleaner
import com.giovannimartins.rreader.textkit.model.Chapter

/**
 * Splits a document's paragraphs into chapters, trying in order:
 * 1. the outline: a chapter starts at the first line remaining on the entry's page (at or below the entry's position
 *    when it has one) and takes the entry's title; entries that point to the same line keep the first title, and
 *    entries outside the document are ignored;
 * 2. headings ([ParagraphRules.isHeading]): a chapter starts at each heading and takes its text as title; a heading
 *    that directly follows another one joins its chapter, so a part title and the chapter after it stay together;
 * 3. page ranges: a chapter, without title, for every [PAGES_PER_CHAPTER] pages, starting at paragraph boundaries.
 *
 * Text before the first outline entry or heading becomes a chapter without title. A way of splitting is used when it
 * gives at least two chapters, or a single chapter in a document of at most [PAGES_PER_CHAPTER] pages. Chapters
 * never lack text.
 */
internal class ChapterSplitter(
    private val lines: List<TextLine>,
    private val pageCount: Int,
    private val paragraphs: ParagraphBuilder,
) {
    fun split(outline: List<OutlineEntry>): List<Chapter> {
        val outlineStarts = outlineStarts(outline)
        val fromOutline =
            if (outlineStarts.isEmpty()) emptyList() else byStarts(paragraphs.build(outlineStarts.keys), outlineStarts)
        if (isUsable(fromOutline)) return fromOutline
        val unforced = paragraphs.build()
        return byHeadings(unforced).takeIf(::isUsable) ?: byPageRanges(unforced)
    }

    private fun isUsable(chapters: List<Chapter>): Boolean =
        chapters.size >= 2 || (chapters.size == 1 && pageCount <= PAGES_PER_CHAPTER)

    /** First-line indexes of the chapters the outline marks, in order, with their titles. */
    private fun outlineStarts(outline: List<OutlineEntry>): Map<Int, String?> {
        val starts = sortedMapOf<Int, String?>()
        outline
            .filter { it.pageIndex in 0 until pageCount }
            .forEach { entry ->
                startLine(entry)?.let { line ->
                    if (line !in starts) starts[line] = TextCleaner.clean(entry.title).ifEmpty { null }
                }
            }
        return starts
    }

    /** The first line on the entry's page, at or below its position, else the first line of a later page. */
    private fun startLine(entry: OutlineEntry): Int? {
        val first = firstLineFrom(entry.pageIndex)
        val top = entry.top
        if (first == null || top == null) return first
        return (first until lines.size).firstOrNull { index ->
            lines[index].pageIndex != entry.pageIndex || lines[index].box.bottom >= top
        }
    }

    /** Index of the first line on page [pageIndex] or after it; lines are in page order. */
    private fun firstLineFrom(pageIndex: Int): Int? {
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].pageIndex < pageIndex) low = middle + 1 else high = middle
        }
        return low.takeIf { it < lines.size }
    }

    private fun byStarts(
        paragraphs: List<Paragraph>,
        starts: Map<Int, String?>,
    ): List<Chapter> {
        val chapters = ChapterCollector()
        val pending = starts.entries.iterator()
        var next = pending.nextOrNull()
        for (paragraph in paragraphs) {
            var title: String? = null
            var startsChapter = false
            while (next != null && next.key <= paragraph.firstLine) {
                if (!startsChapter) title = next.value
                startsChapter = true
                next = pending.nextOrNull()
            }
            if (startsChapter) chapters.start(title)
            chapters.add(paragraph)
        }
        return chapters.finish()
    }

    private fun byHeadings(paragraphs: List<Paragraph>): List<Chapter> {
        val chapters = ChapterCollector()
        var bodySinceHeading = true
        for (paragraph in paragraphs) {
            if (paragraph.isHeading && bodySinceHeading) chapters.start(paragraph.text)
            bodySinceHeading = !paragraph.isHeading
            chapters.add(paragraph)
        }
        return chapters.finish()
    }

    private fun byPageRanges(paragraphs: List<Paragraph>): List<Chapter> {
        val chapters = ChapterCollector()
        var range = 0
        for (paragraph in paragraphs) {
            val paragraphRange = paragraph.pageIndex / PAGES_PER_CHAPTER
            if (paragraphRange != range) chapters.start(null)
            range = paragraphRange
            chapters.add(paragraph)
        }
        return chapters.finish()
    }

    private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

    /** Accumulates paragraphs into chapters; a chapter is only produced when it has text. */
    private class ChapterCollector {
        private val chapters = mutableListOf<Chapter>()
        private val texts = mutableListOf<String>()
        private var title: String? = null

        fun start(chapterTitle: String?) {
            flush()
            title = chapterTitle
        }

        fun add(paragraph: Paragraph) {
            texts += paragraph.text
        }

        fun finish(): List<Chapter> {
            flush()
            return chapters
        }

        private fun flush() {
            if (texts.isNotEmpty()) chapters += Chapter.fromParagraphs(title, texts.toList())
            texts.clear()
        }
    }

    companion object {
        /** Pages per chapter when neither the outline nor headings give chapters. */
        const val PAGES_PER_CHAPTER: Int = 10
    }
}
