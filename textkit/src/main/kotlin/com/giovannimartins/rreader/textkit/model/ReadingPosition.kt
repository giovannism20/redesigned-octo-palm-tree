package com.giovannimartins.rreader.textkit.model

/**
 * Where the listener is: a character offset into the text of one chapter.
 * This is the single source of truth for progress (see ADR 0006); times and
 * percentages are derived estimates.
 */
public data class ReadingPosition(
    val chapterIndex: Int,
    val charOffset: Int,
) : Comparable<ReadingPosition> {
    init {
        require(chapterIndex >= 0) { "chapterIndex must not be negative" }
        require(charOffset >= 0) { "charOffset must not be negative" }
    }

    override fun compareTo(other: ReadingPosition): Int =
        compareValuesBy(this, other, ReadingPosition::chapterIndex, ReadingPosition::charOffset)

    public companion object {
        /** The very beginning of a book. */
        public val START: ReadingPosition = ReadingPosition(0, 0)
    }
}
