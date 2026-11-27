package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.gutenberg.GutenbergBoilerplate
import com.giovannimartins.rreader.textkit.model.Chapter

/** A paragraph where a chapter starts and the title it brings, if any. */
internal data class ChapterStart(
    val paragraph: Int,
    val title: String?,
    /** False for the start of a document the table of contents points to as a whole, which never moves. */
    val movable: Boolean = true,
)

/**
 * Splits the text of the spine documents into chapters, in reading order:
 *
 * 1. **Table of contents.** The first table of contents that points to a spine document gives the starts
 *    ([TocStarts]): an entry for an element starts a chapter at that element (anchors, see
 *    [ContentDocument.anchors]), so a file holding many chapters is split, and an entry for a whole document, or for
 *    an element the document lacks, starts one at the document's first paragraph. Text before the first start of a
 *    document stays with the chapter before it. Of a nested table of contents, element entries only count down to
 *    the depth [TocDepth] chooses. Without a table of contents that fits, every document starts a chapter.
 * 2. **Headings before an anchor.** A start at an element takes the headings right before it in its document when
 *    they follow body text or open the document, so a chapter number printed above the title the table of contents
 *    points to (`<h5>II</h5><h4 id="c2">Do livro.</h4>`) opens its chapter instead of closing the one before.
 * 3. **Titles.** Text before the first start forms a leading chapter. A chapter is titled by the table of contents,
 *    else by its first `h1`–`h3` heading. Chapters without text are dropped.
 * 4. **Project Gutenberg.** The header and license Gutenberg adds are removed ([GutenbergBoilerplate]).
 * 5. **Short chapters** join a neighbour in the same document ([ShortChapters]).
 */
internal object ChapterAssembler {
    /** Sections longer than this many characters (about an hour of listening) are too long for one chapter. */
    const val LONG_SECTION_LENGTH: Long = 50_000

    fun assemble(
        documents: List<SpineDocument>,
        tablesOfContents: Sequence<List<TocEntry>>,
    ): List<Chapter> {
        val text = SpineText(documents)
        val paths = documents.mapTo(HashSet()) { it.path }
        val toc = tablesOfContents.firstOrNull { entries -> entries.any { it.path in paths } }
        val starts = pullBack(toc?.let { TocStarts.resolve(it, text) } ?: everyDocument(documents, text), text)
        return ShortChapters.merge(withoutGutenbergBoilerplate(chapters(starts, text)))
    }

    private fun everyDocument(
        documents: List<SpineDocument>,
        text: SpineText,
    ): List<ChapterStart> =
        merged(documents.indices.map { ChapterStart(text.documentStart(it), title = null, movable = false) })

    /** One start per paragraph, in order, titled by the first start there that has a title. */
    private fun merged(starts: List<ChapterStart>): List<ChapterStart> =
        starts
            .groupBy { it.paragraph }
            .map { (paragraph, same) ->
                ChapterStart(paragraph, same.firstNotNullOfOrNull { it.title }, same.all { it.movable })
            }.sortedBy { it.paragraph }

    /** [starts] with each movable start moved back over the headings right before it (rule 2). */
    private fun pullBack(
        starts: List<ChapterStart>,
        text: SpineText,
    ): List<ChapterStart> {
        val paragraphs = starts.mapTo(HashSet()) { it.paragraph }
        return starts.map { start ->
            if (start.movable) start.copy(paragraph = headed(start.paragraph, paragraphs, text)) else start
        }
    }

    /**
     * The first paragraph of the headings right before [paragraph] in its document, when body text or the start of
     * the document comes before them and none of them starts a chapter; otherwise [paragraph].
     */
    private fun headed(
        paragraph: Int,
        starts: Set<Int>,
        text: SpineText,
    ): Int {
        var first = paragraph
        while (!text.opensDocument(first) && first - 1 !in starts && text.isHeading(first - 1)) first--
        val afterBody = text.opensDocument(first) || !text.isHeading(first - 1)
        return if (afterBody) first else paragraph
    }

    /** The chapters between consecutive [starts], the text before the first start included (rule 3). */
    private fun chapters(
        starts: List<ChapterStart>,
        text: SpineText,
    ): List<AssembledChapter> {
        val all = if (starts.firstOrNull()?.paragraph == 0) starts else listOf(ChapterStart(0, null)) + starts
        val ends = all.drop(1).map { it.paragraph } + text.size
        return all.zip(ends).filter { (start, end) -> end > start.paragraph }.map { (start, end) ->
            val title = start.title ?: text.titleIn(start.paragraph, end)
            val chapter = Chapter.fromParagraphs(title, text.paragraphs.subList(start.paragraph, end))
            AssembledChapter(chapter, text.opensDocument(start.paragraph))
        }
    }

    private fun withoutGutenbergBoilerplate(chapters: List<AssembledChapter>): List<AssembledChapter> =
        GutenbergBoilerplate
            .stripIndexed(chapters.map { it.chapter })
            ?.map { (index, chapter) -> AssembledChapter(chapter, chapters[index].opensDocument) }
            ?: chapters
}
