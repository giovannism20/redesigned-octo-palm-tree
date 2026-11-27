package com.giovannimartins.rreader.textkit.estimate

import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * How fast a voice speaks at 1.0×, in characters of normalized text per second. Time estimates divide by it;
 * it is learned from rendered segments with [updatedWith].
 */
public data class SpeakingRate(
    val charsPerSecond: Double,
) {
    init {
        require(charsPerSecond.isFinite() && charsPerSecond > 0.0) {
            "charsPerSecond must be positive and finite, was $charsPerSecond"
        }
    }

    /**
     * This rate blended with a measured sample by an exponential moving average: `rate + weight × (sample −
     * rate)`. A sample is [sampleChars] of text that took [sampleDuration] to speak at 1.0×.
     *
     * Degenerate samples leave the rate unchanged: no characters, a duration that is not positive or is
     * infinite, or a rate outside 1–100 characters per second, which no voice produces.
     *
     * @param weight how much the sample counts, from 0.0 (ignored) to 1.0 (replaces the rate).
     */
    public fun updatedWith(
        sampleChars: Int,
        sampleDuration: Duration,
        weight: Double = DEFAULT_SAMPLE_WEIGHT,
    ): SpeakingRate {
        require(weight in 0.0..1.0) { "weight must be between 0 and 1, was $weight" }
        val sampleRate = sampleRateOf(sampleChars, sampleDuration) ?: return this
        return SpeakingRate(charsPerSecond + weight * (sampleRate - charsPerSecond))
    }

    private fun sampleRateOf(
        chars: Int,
        duration: Duration,
    ): Double? {
        val measurable = chars > 0 && duration.isPositive() && duration.isFinite()
        return if (measurable) {
            (chars / duration.toDouble(DurationUnit.SECONDS)).takeIf { it in PLAUSIBLE_RATES }
        } else {
            null
        }
    }

    public companion object {
        /** Typical Portuguese narration, used until a voice has been measured. */
        public val DEFAULT: SpeakingRate = SpeakingRate(charsPerSecond = 14.5)

        /** Default weight of a new sample in [updatedWith]. */
        public const val DEFAULT_SAMPLE_WEIGHT: Double = 0.2

        private val PLAUSIBLE_RATES = 1.0..100.0
    }
}
