package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.model.TextRange
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A chapter's text and the segments it is rendered in. */
internal class ChapterPlan(
    val index: Int,
    val title: String?,
    val text: String,
    val segments: List<TextRange>,
) {
    /**
     * End of the span of segment [segmentIndex]: the start of the next segment, or the end of the chapter.
     * Spans cover the whitespace between segments, so positions advance without gaps.
     */
    fun spanEnd(segmentIndex: Int): Int = segments.getOrNull(segmentIndex + 1)?.start ?: text.length
}

/** Segment [index] of [plan]. */
internal data class SegmentCursor(
    val plan: ChapterPlan,
    val index: Int,
) {
    val range: TextRange get() = plan.segments[index]

    /** Where the segment starts, which is where playback resumes. */
    val position: ReadingPosition get() = ReadingPosition(plan.index, range.start)

    /** The text to speak: the segment's chapter text in speech form. */
    fun text(): String = plan.text.substring(range.start, range.end)

    /** Whether the segment is the chapter's heading (its first segment and equal to the chapter title). */
    fun isHeading(): Boolean = index == 0 && plan.title?.trim()?.equals(text().trim(), ignoreCase = true) == true
}

/**
 * Loads and plans the chapters around the listening position, keeping the last few in memory. Confined to
 * the player's thread; text is read and planned on other dispatchers.
 */
internal class ChapterPlans(
    private val content: PlaybackContent,
    private val planner: SegmentPlanner,
    private val cpu: CoroutineDispatcher,
) {
    private var book: PlaybackBook? = null
    private val cache = LinkedHashMap<Int, ChapterPlan>(MAX_CACHED + 1, LOAD_FACTOR, true)

    /** Forgets every plan and plans [book] from now on. */
    fun reset(book: PlaybackBook?) {
        this.book = book
        cache.clear()
    }

    /** Characters of chapter [chapterIndex] of the book being planned, or null when it has no such chapter. */
    fun chapterLength(chapterIndex: Int): Int? = book?.chapters?.getOrNull(chapterIndex)?.length

    /** End of the span that holds [position], if its chapter is already planned. */
    fun spanEndAt(position: ReadingPosition): Int? =
        cache[position.chapterIndex]?.let { plan ->
            plan.spanEnd(planner.segmentAt(plan.segments, position.charOffset))
        }

    /**
     * The segment that holds [position], or the next one when it falls between segments or past the end of
     * its chapter; null at the end of the book. With a positive [rewindChars], the segment that holds the
     * rewound position instead: one segment or [rewindChars] characters earlier, whichever is the smaller
     * rewind ([ResumeRewind.target]).
     *
     * @throws java.io.IOException when a chapter's text cannot be read.
     */
    suspend fun locate(
        position: ReadingPosition,
        rewindChars: Int = 0,
    ): SegmentCursor? {
        val holding = holding(position)
        val previous = holding?.takeIf { rewindChars > 0 }?.let { before(it) }
        return if (previous == null) {
            holding
        } else {
            holding(ResumeRewind.target(position, previous.position, rewindChars, book?.chapterLengths.orEmpty()))
        }
    }

    /**
     * The segment after [cursor], continuing into the next chapters; null at the end of the book.
     *
     * @throws java.io.IOException when a chapter's text cannot be read.
     */
    suspend fun after(cursor: SegmentCursor): SegmentCursor? =
        if (cursor.index + 1 < cursor.plan.segments.size) {
            SegmentCursor(cursor.plan, cursor.index + 1)
        } else {
            firstFrom(cursor.plan.index + 1)
        }

    private suspend fun holding(position: ReadingPosition): SegmentCursor? {
        val plan = plan(position.chapterIndex) ?: return null
        val index = planner.segmentAt(plan.segments, position.charOffset)
        return if (index < plan.segments.size) SegmentCursor(plan, index) else firstFrom(plan.index + 1)
    }

    /** The segment before [cursor], going back into the previous chapters; null at the first of the book. */
    private suspend fun before(cursor: SegmentCursor): SegmentCursor? {
        if (cursor.index > 0) return SegmentCursor(cursor.plan, cursor.index - 1)
        var index = cursor.plan.index - 1
        var plan = plan(index)
        while (plan != null && plan.segments.isEmpty()) {
            index--
            plan = plan(index)
        }
        return plan?.let { SegmentCursor(it, it.segments.lastIndex) }
    }

    private suspend fun firstFrom(chapterIndex: Int): SegmentCursor? {
        var index = chapterIndex
        var plan = plan(index)
        while (plan != null && plan.segments.isEmpty()) {
            index++
            plan = plan(index)
        }
        return plan?.let { SegmentCursor(it, 0) }
    }

    private suspend fun plan(index: Int): ChapterPlan? {
        val book = book
        val chapter = book?.chapters?.getOrNull(index)
        return when {
            book == null || chapter == null -> null
            else -> cache[index] ?: load(book.id, index, chapter.title).also { cache[index] = it }.also { evict() }
        }
    }

    private suspend fun load(
        bookId: String,
        index: Int,
        title: String?,
    ): ChapterPlan {
        val text = content.chapterText(bookId, index)
        return ChapterPlan(index, title, text, withContext(cpu) { planner.plan(text) })
    }

    private fun evict() {
        while (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
    }

    private companion object {
        /** The playing chapter, the next one being rendered ahead and the previous one for seeks back. */
        const val MAX_CACHED = 3
        const val LOAD_FACTOR = 0.75f
    }
}
