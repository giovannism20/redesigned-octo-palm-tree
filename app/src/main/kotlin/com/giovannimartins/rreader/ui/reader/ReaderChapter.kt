package com.giovannimartins.rreader.ui.reader

import com.giovannimartins.rreader.playback.SpokenSegment
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner

/** Chapter [chapterIndex] of book [bookId], as the reader shows it. */
internal data class ChapterKey(
    val bookId: String,
    val chapterIndex: Int,
)

/** Characters [start] to [end] of paragraph [paragraph] are being read. */
data class ReaderHighlight(
    val paragraph: Int,
    val start: Int,
    val end: Int,
)

/**
 * A chapter's text as the reader lays it out: one item per paragraph, and the segments the player reads it
 * in, planned with the same [planner] so a tapped sentence maps to the segment the player resumes from.
 * Offsets are the chapter's (ADR 0006). Build it off the main thread: planning a long chapter takes a while.
 */
internal class ReaderChapter(
    val key: ChapterKey,
    text: String,
    private val planner: SegmentPlanner,
) {
    private val ranges = Chapter.paragraphRangesOf(text)
    private val segments = planner.plan(text)

    /** The chapter's paragraphs, in order. */
    val paragraphs: List<String> = ranges.map { text.substring(it.start, it.end) }

    /** Where [spoken] is in this chapter's paragraphs, or null when it is not in this chapter. */
    fun highlightOf(spoken: SpokenSegment?): ReaderHighlight? =
        spoken?.takeIf { it.bookId == key.bookId && it.chapterIndex == key.chapterIndex }?.let { segment ->
            paragraphAt(segment.range.start)?.let { index ->
                val paragraph = ranges[index]
                ReaderHighlight(
                    paragraph = index,
                    start = segment.range.start - paragraph.start,
                    end = segment.range.end.coerceAtMost(paragraph.end) - paragraph.start,
                )
            }
        }

    /**
     * Where reading starts after a tap on character [offset] of paragraph [paragraph]: the start of the segment
     * there, or of the next one when the tap falls between sentences (the last one at the very end). Null for
     * a paragraph the chapter does not have.
     */
    fun readingPositionAt(
        paragraph: Int,
        offset: Int,
    ): ReadingPosition? =
        ranges.getOrNull(paragraph)?.let { range ->
            val chapterOffset = range.start + offset.coerceIn(0, range.length)
            val segment = segments.getOrNull(planner.segmentAt(segments, chapterOffset)) ?: segments.lastOrNull()
            segment?.let { ReadingPosition(key.chapterIndex, it.start) }
        }

    /** The paragraph that holds chapter offset [offset], or the next one when it falls between paragraphs. */
    private fun paragraphAt(offset: Int): Int? {
        val index =
            ranges.binarySearch { range ->
                when {
                    range.end <= offset -> -1
                    range.start > offset -> 1
                    else -> 0
                }
            }
        return (if (index >= 0) index else -(index + 1)).takeIf { it < ranges.size }
    }
}
