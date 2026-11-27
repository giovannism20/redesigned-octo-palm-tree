package com.giovannimartins.rreader.textkit.gutenberg

import com.giovannimartins.rreader.textkit.model.Chapter

/**
 * Removes the text Project Gutenberg adds around its books, so that it is not read aloud:
 *
 * - the English header up to and including the `*** START OF THE PROJECT GUTENBERG EBOOK … ***` line (title, author,
 *   release date and terms of use);
 * - up to [MAX_CREDIT_PARAGRAPHS] production credits right after that line, such as "Produced by … and the Online
 *   Distributed Proofreading Team at https://www.pgdp.net";
 * - everything from the `*** END OF THE PROJECT GUTENBERG EBOOK … ***` line, or from an "End of the Project Gutenberg
 *   EBook of …" line before it, to the end of the book: the license, the donation appeal and the links.
 *
 * The markers are recognized in the spellings Gutenberg used over the years (`THE` or `THIS`, `EBOOK`, `E-BOOK` or
 * `ETEXT`, with or without spaces after the asterisks, in any letter case), as a paragraph of their own or inside a
 * longer one, as older books kept the header in a single preformatted block: the rest of such a paragraph is kept. The
 * start marker is looked for in the first [HEADER_PARAGRAPHS] paragraphs of the book; the end marker after it.
 *
 * Paragraphs are removed, never rewritten, so the book's own text stays exactly as it was. Chapters left without text
 * are dropped, and a remaining chapter titled after Project Gutenberg (such as "The Project Gutenberg eBook of …",
 * taken from the header's heading) loses that title. A book without markers, or whose markers would leave no text, is
 * returned unchanged.
 */
public object GutenbergBoilerplate {
    /** How many paragraphs from the start of a book may hold the start marker. */
    public const val HEADER_PARAGRAPHS: Int = 100

    /** How many paragraphs right after the start marker may be production credits. */
    public const val MAX_CREDIT_PARAGRAPHS: Int = 3

    private const val NOT_FOUND = -1
    private const val MAX_CREDIT_LENGTH = 300

    /** "THE PROJECT GUTENBERG EBOOK" as the markers spell it, after `START OF` or `END OF`. */
    private const val THE_EBOOK = "(?:THE\\s+|THIS\\s+)?PROJECT\\s+GUTENBERG(?:['’]S)?\\s+E-?(?:BOOK|TEXT)\\b"

    /**
     * The asterisks that open a marker: a run of at least two, matched only from its first asterisk and never given
     * back (possessive, like the spaces after it). A backtracking `\*{2,}\s*` retried every position of a long run
     * of asterisks, which took quadratic time on a hostile paragraph; this way a run is scanned once.
     */
    private const val OPENING = "(?<!\\*)\\*{2,}+\\s*+"

    /** The start marker, up to its closing asterisks or the end of the paragraph. */
    private val START = marker("${OPENING}START\\s+OF\\s+$THE_EBOOK.*?(?:\\*{2,}|$)")
    private val END = marker("${OPENING}END\\s+OF\\s+$THE_EBOOK")

    /** "End of the Project Gutenberg EBook of …" or "End of Project Gutenberg's …", opening a paragraph. */
    private val END_LINE = marker("^\\W{0,3}END\\s+OF\\s+(?:THE\\s+|THIS\\s+)?PROJECT\\s+GUTENBERG\\b")
    private val CREDITS =
        marker(
            "^(?:PRODUCED\\s+BY|THIS\\s+E-?(?:BOOK|TEXT)\\s+WAS\\s+(?:PRODUCED|PREPARED)\\s+BY|" +
                "E-?TEXT\\s+PREPARED\\s+BY|TRANSCRIBED\\s+(?:FROM\\s.+?\\s)?BY)\\b|DISTRIBUTED\\s+PROOFREAD|PGDP\\.NET",
        )
    private val GUTENBERG_TITLE = marker("PROJECT\\s+GUTENBERG")

    /** [chapters] without Project Gutenberg's header, credits and license, as described on [GutenbergBoilerplate]. */
    public fun strip(chapters: List<Chapter>): List<Chapter> = stripIndexed(chapters)?.map { it.value } ?: chapters

    /**
     * Like [strip], but each remaining chapter comes with the index in [chapters] of the chapter it was taken from.
     * Null when [chapters] are returned unchanged.
     */
    internal fun stripIndexed(chapters: List<Chapter>): List<IndexedValue<Chapter>>? {
        val paragraphs = chapters.flatMapIndexed { index, chapter -> chapter.paragraphs().map { Paragraph(index, it) } }
        val start = paragraphs.take(HEADER_PARAGRAPHS).indexOfFirst { START.containsMatchIn(it.text) }
        val end = (start + 1 until paragraphs.size).firstOrNull { isEnd(paragraphs[it].text) } ?: NOT_FOUND
        if (start == NOT_FOUND && end == NOT_FOUND) return null
        val kept = paragraphs.mapIndexedNotNull { index, paragraph -> keep(paragraph, index, start, end) }
        val body = if (start == NOT_FOUND) kept else kept.dropLeadingCredits()
        return rebuild(chapters, body).ifEmpty { null }
    }

    private fun isEnd(text: String): Boolean = END.containsMatchIn(text) || END_LINE.containsMatchIn(text)

    /** What is left of the paragraph at [index], given where the start and end markers are. */
    private fun keep(
        paragraph: Paragraph,
        index: Int,
        start: Int,
        end: Int,
    ): Paragraph? {
        val text =
            when {
                index < start -> null
                index == start -> START.find(paragraph.text)?.let { paragraph.text.substring(it.range.last + 1) }
                end != NOT_FOUND && index > end -> null
                index == end -> END.find(paragraph.text)?.let { paragraph.text.substring(0, it.range.first) }
                else -> paragraph.text
            }?.trim()
        return if (text.isNullOrEmpty()) null else paragraph.copy(text = text)
    }

    private fun List<Paragraph>.dropLeadingCredits(): List<Paragraph> {
        val credits =
            take(MAX_CREDIT_PARAGRAPHS)
                .takeWhile { it.text.length <= MAX_CREDIT_LENGTH && CREDITS.containsMatchIn(it.text) }
                .size
        return drop(credits)
    }

    private fun rebuild(
        chapters: List<Chapter>,
        body: List<Paragraph>,
    ): List<IndexedValue<Chapter>> {
        val byChapter = body.groupBy({ it.chapter }, { it.text })
        return chapters.mapIndexedNotNull { index, chapter ->
            byChapter[index]?.let { texts ->
                val title = chapter.title?.takeUnless(GUTENBERG_TITLE::containsMatchIn)
                IndexedValue(index, Chapter.fromParagraphs(title, texts))
            }
        }
    }

    private fun Chapter.paragraphs(): List<String> = paragraphRanges().map { text.substring(it.start, it.end) }

    private fun marker(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /** A paragraph's text and the index of the chapter it belongs to. */
    private data class Paragraph(
        val chapter: Int,
        val text: String,
    )
}
