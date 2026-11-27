package com.giovannimartins.rreader.textkit.estimate

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Progress, time left and position arithmetic derived from a [ReadingPosition] (see ADR 0006).
 *
 * `chapterLengths` holds the length of each chapter's normalized text, in book order. Positions are clamped
 * into the book first: a chapter index past the last chapter means the end of the book, and an offset past
 * the end of its chapter means the end of that chapter. Lengths must not be negative.
 */
public object ReadingEstimates {
    /** Fraction of the book's characters before [position], from 0.0 to 1.0; 0.0 for a book without text. */
    public fun progress(
        position: ReadingPosition,
        chapterLengths: List<Int>,
    ): Double {
        val total = totalLength(chapterLengths)
        return if (total == 0L) 0.0 else (offsetOf(position, chapterLengths).toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** Number of characters from [position] to the end of the book. */
    public fun remainingCharacters(
        position: ReadingPosition,
        chapterLengths: List<Int>,
    ): Long = totalLength(chapterLengths) - offsetOf(position, chapterLengths)

    /**
     * How long [remainingChars] take to speak at [rate] and playback [speed] (1.0 is normal speed); zero when
     * nothing remains.
     */
    public fun remainingDuration(
        remainingChars: Long,
        rate: SpeakingRate,
        speed: Float,
    ): Duration {
        requireValidSpeed(speed)
        return if (remainingChars <= 0) Duration.ZERO else (remainingChars / (rate.charsPerSecond * speed)).seconds
    }

    /**
     * How many characters are spoken in [duration] at [rate] and playback [speed], rounded to the nearest
     * character (halves to even, so opposite durations give opposite counts). A negative duration gives a
     * negative count, ready for [shift] backwards.
     */
    public fun charactersFor(
        duration: Duration,
        rate: SpeakingRate,
        speed: Float,
    ): Int {
        requireValidSpeed(speed)
        val chars = round(duration.toDouble(DurationUnit.SECONDS) * rate.charsPerSecond * speed)
        return chars.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * [position] moved by [deltaChars] characters, forwards or backwards, across chapter boundaries and
     * clamped to the start and end of the book. A result on a chapter boundary is the start of the next
     * non-empty chapter; the end of the book is the end of the last chapter. Without chapters the result is
     * [ReadingPosition.START].
     */
    public fun shift(
        position: ReadingPosition,
        deltaChars: Int,
        chapterLengths: List<Int>,
    ): ReadingPosition {
        val total = totalLength(chapterLengths)
        val target = (offsetOf(position, chapterLengths) + deltaChars).coerceIn(0L, total)
        return if (chapterLengths.isEmpty()) ReadingPosition.START else positionAt(target, chapterLengths)
    }

    /** Characters of the book before [position], with the position clamped into the book. */
    private fun offsetOf(
        position: ReadingPosition,
        chapterLengths: List<Int>,
    ): Long {
        val before = chapterLengths.asSequence().take(position.chapterIndex).sumOf { it.toLong() }
        val within = chapterLengths.getOrNull(position.chapterIndex)?.let { minOf(position.charOffset, it) } ?: 0
        return before + within
    }

    /** The position of the [offset]-th character of the book; [offset] is within `0..total`. */
    private fun positionAt(
        offset: Long,
        chapterLengths: List<Int>,
    ): ReadingPosition {
        var chapterStart = 0L
        for ((index, length) in chapterLengths.withIndex()) {
            if (offset < chapterStart + length) return ReadingPosition(index, (offset - chapterStart).toInt())
            chapterStart += length
        }
        return ReadingPosition(chapterLengths.lastIndex, chapterLengths.last())
    }

    private fun totalLength(chapterLengths: List<Int>): Long {
        require(chapterLengths.all { it >= 0 }) { "Chapter lengths must not be negative" }
        return chapterLengths.sumOf { it.toLong() }
    }

    private fun requireValidSpeed(speed: Float) {
        require(speed.isFinite() && speed > 0f) { "speed must be positive and finite, was $speed" }
    }
}
