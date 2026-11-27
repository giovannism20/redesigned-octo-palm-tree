package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.TextRange
import java.io.File
import kotlin.time.Duration.Companion.minutes

/** A rendered segment in the internal player's playlist. */
internal data class QueuedSegment(
    val chapterIndex: Int,
    val range: TextRange,
    /** End of the segment's span (see [ChapterPlan.spanEnd]). */
    val spanEnd: Int,
    val file: File,
    val durationMillis: Long,
) {
    /** Media id of the segment's item in the internal playlist. */
    val key: String = "$chapterIndex:${range.start}-${range.end}"

    /** Characters from the segment's start to the end of its span. */
    val spanLength: Int get() = spanEnd - range.start
}

/** Rendered audio waiting after the playing segment. */
internal data class Lookahead(
    val segments: Int,
    val durationMillis: Long,
)

/** How much audio is kept rendered ahead of the playing segment: at least two minutes and four segments. */
internal object RenderAhead {
    const val MIN_SEGMENTS = 4
    val MIN_DURATION = 2.minutes

    fun wantsMore(lookahead: Lookahead): Boolean =
        lookahead.segments < MIN_SEGMENTS || lookahead.durationMillis < MIN_DURATION.inWholeMilliseconds
}

/** The segments in the internal player's playlist, in the same order as its items. */
internal class SegmentQueue {
    private val entries = ArrayList<QueuedSegment>()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun add(segment: QueuedSegment) {
        entries += segment
    }

    fun clear() = entries.clear()

    /** The queued segment whose media id is [key], if any. */
    fun find(key: String?): QueuedSegment? = key?.let { entries.firstOrNull { it.key == key } }

    fun last(): QueuedSegment? = entries.lastOrNull()

    /** Segments queued after the one whose media id is [currentKey]; all of them when it is not queued. */
    fun lookahead(currentKey: String?): Lookahead {
        val after = entries.subList(entries.indexOfFirst { it.key == currentKey } + 1, entries.size)
        return Lookahead(after.size, after.sumOf { it.durationMillis })
    }

    /** Removes the first [count] segments, which the player has already played. */
    fun dropFirst(count: Int) {
        entries.subList(0, count.coerceIn(0, entries.size)).clear()
    }
}
