package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Learns how fast each voice speaks from the segments it renders, so chapter durations and seeks in time are
 * close to what the listener hears. Rates start at [SpeakingRate.DEFAULT] and move by an exponential moving
 * average ([SpeakingRate.updatedWith]). Kept in memory; safe to use from any thread.
 */
@Singleton
class SpeakingRateTracker
    @Inject
    constructor() {
        private val rates = ConcurrentHashMap<String, SpeakingRate>()

        /** The current estimate for [voiceId], or the default when the voice was never measured (or is null). */
        fun rateFor(voiceId: String?): SpeakingRate = voiceId?.let(rates::get) ?: SpeakingRate.DEFAULT

        /**
         * Blends one rendered segment into the voice's rate: [chars] of chapter text took [duration] to speak at
         * 1.0×. Short segments are ignored because the engine's leading and trailing silence dominates them.
         */
        fun record(
            voiceId: String,
            chars: Int,
            duration: Duration,
        ) {
            if (chars < MIN_SAMPLE_CHARS) return
            rates.compute(voiceId) { _, current -> (current ?: SpeakingRate.DEFAULT).updatedWith(chars, duration) }
        }

        private companion object {
            const val MIN_SAMPLE_CHARS = 40
        }
    }
