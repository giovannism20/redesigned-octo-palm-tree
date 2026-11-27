package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import kotlin.time.Duration.Companion.milliseconds

/**
 * The facade player's media time (ADR 0006): a chapter lasts as long as its characters take at [rate] and
 * 1.0×, so positions and durations shown to the listener are estimates derived from character offsets.
 * Playback speed does not change media time; it changes how fast media time passes.
 */
internal data class ChapterClock(
    val rate: SpeakingRate,
) {
    /** Media time of [chars] characters, in milliseconds. */
    fun millisFor(chars: Int): Long =
        ReadingEstimates.remainingDuration(chars.toLong(), rate, NORMAL_SPEED).inWholeMilliseconds

    /** Characters spoken in [millis] of media time; negative for a negative time. */
    fun charsFor(millis: Long): Int = ReadingEstimates.charactersFor(millis.milliseconds, rate, NORMAL_SPEED)

    private companion object {
        const val NORMAL_SPEED = 1f
    }
}
