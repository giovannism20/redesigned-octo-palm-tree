package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlin.time.Duration

/**
 * Where the facade player's seek commands land, as reading positions (ADR 0006). The player then resumes at
 * the start of the segment that holds the target.
 */
internal object SeekTargets {
    /**
     * [from] moved by [delta] of media time across chapters, clamped to the start and end of the book
     * (see [ReadingEstimates.shift]). A forward move never stays inside the span the listener is in, which
     * ends at [spanEnd], so +15 s always makes progress even in a segment longer than 15 s.
     */
    fun offset(
        from: ReadingPosition,
        delta: Duration,
        chapterLengths: List<Int>,
        clock: ChapterClock,
        spanEnd: Int?,
    ): ReadingPosition {
        val deltaChars = clock.charsFor(delta.inWholeMilliseconds)
        val target = ReadingEstimates.shift(from, deltaChars, chapterLengths)
        val skipTo =
            spanEnd?.takeIf { end ->
                deltaChars > 0 && target.chapterIndex == from.chapterIndex && target.charOffset < end
            }
        // A span may end at the end of its chapter; shifting by zero moves that onto the next chapter.
        return skipTo?.let { ReadingEstimates.shift(ReadingPosition(from.chapterIndex, it), 0, chapterLengths) }
            ?: target
    }

    /**
     * [positionMillis] of media time into chapter [chapterIndex], kept inside the chapter so seeking to its
     * very end plays its last segment. Null when the chapter does not exist.
     */
    fun inChapter(
        chapterIndex: Int,
        positionMillis: Long,
        chapterLengths: List<Int>,
        clock: ChapterClock,
    ): ReadingPosition? =
        chapterLengths.getOrNull(chapterIndex)?.let { length ->
            val offset = clock.charsFor(positionMillis.coerceAtLeast(0)).coerceIn(0, (length - 1).coerceAtLeast(0))
            ReadingPosition(chapterIndex, offset)
        }
}
