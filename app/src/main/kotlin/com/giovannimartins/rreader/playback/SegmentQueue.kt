package com.giovannimartins.rreader.playback

import androidx.media3.common.PlaybackParameters
import com.giovannimartins.rreader.textkit.model.TextRange
import com.giovannimartins.rreader.tts.RenderSpeeds
import com.giovannimartins.rreader.tts.Voice
import java.io.File
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes

/**
 * A rendered segment in the internal player's playlist: [file] was rendered at [renderSpeed] and lasts
 * [durationMillis] heard at 1.0× of the voice's own pace (its own length times [renderSpeed]).
 */
internal data class QueuedSegment(
    val chapterIndex: Int,
    val range: TextRange,
    /** End of the segment's span (see [ChapterPlan.spanEnd]). */
    val spanEnd: Int,
    val file: File,
    val durationMillis: Long,
    val renderSpeed: Float = RenderSpeeds.NORMAL,
) {
    /** The file's own length. */
    val mediaMillis: Long get() = (durationMillis / renderSpeed).roundToLong()

    /** A position in the file, as time heard at 1.0× of the voice's own pace. */
    fun atNormalSpeed(mediaPositionMillis: Long): Long = (mediaPositionMillis * renderSpeed).roundToLong()

    /** Media id of the segment's item in the internal playlist. */
    val key: String = "$chapterIndex:${range.start}-${range.end}"

    /** Characters from the segment's start to the end of its span. */
    val spanLength: Int get() = spanEnd - range.start
}

/**
 * How fast the internal player plays a segment so the listener hears it at the listening speed. A voice that renders
 * at speed already spoke it faster or slower ([RenderSpeeds]), so only what is left is stretched: nothing when the
 * segment was rendered at this speed, the difference for audio rendered before the speed changed. With no segment
 * playing yet, the next one is assumed to be rendered for this speed. A pitch of 1.0 keeps the voice natural.
 */
internal object SegmentSpeed {
    fun parameters(
        listening: Float,
        playing: QueuedSegment?,
        voice: Voice?,
    ): PlaybackParameters {
        val rendered =
            playing?.renderSpeed
                ?: if (voice?.rendersAtSpeed == true) RenderSpeeds.of(listening) else RenderSpeeds.NORMAL
        return PlaybackParameters(listening / rendered)
    }
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

    /**
     * Audio left in the chapter of the segment whose media id is [currentKey], [positionMillis] into it (both at
     * 1.0×). Null when that segment is not queued, or the chapter's last segment may not be queued yet: no later
     * chapter is queued, the last queued segment of the chapter does not reach its end ([chapterLength]) and
     * the book has more to render ([exhausted] is false).
     */
    fun audioLeftInChapter(
        currentKey: String?,
        positionMillis: Long,
        chapterLength: Int,
        exhausted: Boolean,
    ): Long? {
        val index = entries.indexOfFirst { it.key == currentKey }
        val current = entries.getOrNull(index) ?: return null
        val rest = entries.subList(index, entries.size)
        val inChapter = rest.takeWhile { it.chapterIndex == current.chapterIndex }
        val complete = inChapter.size < rest.size || inChapter.last().spanEnd >= chapterLength || exhausted
        return if (complete) {
            inChapter.sumOf { it.durationMillis } - positionMillis.coerceIn(0, current.durationMillis)
        } else {
            null
        }
    }

    /** Removes the first [count] segments, which the player has already played. */
    fun dropFirst(count: Int) {
        entries.subList(0, count.coerceIn(0, entries.size)).clear()
    }
}
