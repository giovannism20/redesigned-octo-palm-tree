package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.TextRange

/**
 * Groups the sentences of a chapter into segments: the unit that is rendered to one audio clip, played as one
 * playlist item and highlighted while it is read (see ADR 0003).
 *
 * A sentence shorter than [minSegmentLength] is merged with the sentence after it when both are in the same
 * paragraph and the result is at most [maxSegmentLength] characters long, so a run like `— Sim. — Não.`
 * becomes one clip instead of several tiny ones. Merging repeats while the segment is still short.
 *
 * Segments are trimmed, ordered, non-overlapping ranges that never cross a paragraph boundary and together
 * cover every non-whitespace character of the chapter. Instances are immutable and thread-safe.
 *
 * @param segmenter splits the chapter into sentences; its [SentenceSegmenter.maxSentenceLength] must not exceed
 *   [maxSegmentLength].
 * @param minSegmentLength segments shorter than this absorb the next sentence when possible.
 * @param maxSegmentLength merging never produces a segment longer than this.
 */
public class SegmentPlanner(
    private val segmenter: SentenceSegmenter = SentenceSegmenter(),
    private val minSegmentLength: Int = DEFAULT_MIN_SEGMENT_LENGTH,
    private val maxSegmentLength: Int = DEFAULT_MAX_SEGMENT_LENGTH,
) {
    init {
        require(minSegmentLength >= 0) { "minSegmentLength must not be negative, was $minSegmentLength" }
        require(maxSegmentLength >= segmenter.maxSentenceLength) {
            "maxSegmentLength ($maxSegmentLength) must fit the longest sentence (${segmenter.maxSentenceLength})"
        }
    }

    /** The segments of [chapterText], in reading order. */
    public fun plan(chapterText: String): List<TextRange> {
        val segments = mutableListOf<TextRange>()
        var current: TextRange? = null
        for (sentence in segmenter.split(chapterText)) {
            current =
                when {
                    current == null -> {
                        sentence
                    }

                    canAbsorb(chapterText, current, sentence) -> {
                        TextRange(current.start, sentence.end)
                    }

                    else -> {
                        segments += current
                        sentence
                    }
                }
        }
        current?.let(segments::add)
        return segments
    }

    /**
     * Index of the segment that contains [charOffset], or of the first segment after it when the offset falls
     * between segments (whitespace). Returns `segments.size` when no segment contains or follows the offset,
     * meaning the chapter is finished. Playback resumes at the start of the returned segment.
     *
     * @param segments ordered, non-overlapping ranges, as returned by [plan].
     */
    public fun segmentAt(
        segments: List<TextRange>,
        charOffset: Int,
    ): Int {
        require(charOffset >= 0) { "charOffset must not be negative, was $charOffset" }
        val index =
            segments.binarySearch { segment ->
                when {
                    segment.end <= charOffset -> -1
                    segment.start > charOffset -> 1
                    else -> 0
                }
            }
        return if (index >= 0) index else -(index + 1)
    }

    private fun canAbsorb(
        text: String,
        segment: TextRange,
        next: TextRange,
    ): Boolean =
        segment.length < minSegmentLength &&
            next.end - segment.start <= maxSegmentLength &&
            !text.substring(segment.end, next.start).contains(Chapter.PARAGRAPH_SEPARATOR)

    public companion object {
        /** Default for `minSegmentLength`: shorter sentences, like most dialogue replies, are merged. */
        public const val DEFAULT_MIN_SEGMENT_LENGTH: Int = 24

        /** Default for `maxSegmentLength`: about 22 seconds of narration. */
        public const val DEFAULT_MAX_SEGMENT_LENGTH: Int = 320
    }
}
