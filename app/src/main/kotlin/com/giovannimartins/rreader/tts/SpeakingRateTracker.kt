package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.di.ApplicationScope
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The speaking rate learned for each voice, and the voice that read most recently. */
data class SpeakingRates(
    val byVoice: Map<String, SpeakingRate> = emptyMap(),
    /** The voice of the last measured segment, or null before any was measured. */
    val lastVoiceId: String? = null,
) {
    /** The rate of [voiceId], or [SpeakingRate.DEFAULT] when it was never measured (or is null). */
    fun rateFor(voiceId: String?): SpeakingRate = voiceId?.let(byVoice::get) ?: SpeakingRate.DEFAULT

    /** These rates with one more sample of [voiceId]: [chars] took [duration] to speak at 1.0×. */
    fun recorded(
        voiceId: String,
        chars: Int,
        duration: Duration,
    ): SpeakingRates = SpeakingRates(byVoice + (voiceId to rateFor(voiceId).updatedWith(chars, duration)), voiceId)

    /** These rates under [newer] ones: voices measured in both keep the newer rate. */
    fun overriddenBy(newer: SpeakingRates): SpeakingRates =
        SpeakingRates(byVoice + newer.byVoice, newer.lastVoiceId ?: lastVoiceId)
}

/** Keeps learned speaking rates across app restarts. */
interface SpeakingRateStore {
    /** The rates saved before; empty when nothing was saved or the store cannot be read. */
    suspend fun load(): SpeakingRates

    suspend fun save(rates: SpeakingRates)
}

/**
 * Learns how fast each voice speaks from the segments it renders, so chapter durations, seeks in time and
 * time-left estimates are close to what the listener hears. Rates start at [SpeakingRate.DEFAULT] and move by
 * an exponential moving average ([SpeakingRate.updatedWith]).
 *
 * Rates survive restarts in the [store]: [restore] loads them once (the player does it before the first book
 * plays) and changes are saved in [scope] at most every [SAVE_INTERVAL], the first one right away. Safe to use
 * from any thread.
 */
@Singleton
class SpeakingRateTracker
    @Inject
    constructor(
        private val store: SpeakingRateStore,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val state = MutableStateFlow(SpeakingRates())
        private val restoring = Mutex()

        @Volatile
        private var restored = false
        private val changed = Channel<Unit>(Channel.CONFLATED)

        init {
            scope.launch {
                changed.consumeEach {
                    store.save(state.value)
                    delay(SAVE_INTERVAL)
                }
            }
        }

        /** Every voice's rate and the voice that read last, starting with the ones restored from the store. */
        val rates: Flow<SpeakingRates> =
            flow {
                restore()
                emitAll(state)
            }

        /** The current estimate for [voiceId], or the default when the voice was never measured (or is null). */
        fun rateFor(voiceId: String?): SpeakingRate = state.value.rateFor(voiceId)

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
            state.update { it.recorded(voiceId, chars, duration) }
            changed.trySend(Unit)
        }

        /** Loads the rates saved in earlier sessions, once; rates measured since the app started win. */
        suspend fun restore() {
            if (restored) return
            restoring.withLock {
                if (!restored) {
                    val saved = store.load()
                    state.update { saved.overriddenBy(it) }
                    restored = true
                }
            }
        }

        private companion object {
            const val MIN_SAMPLE_CHARS = 40

            /** Rates move slowly, so saving twice a minute at most loses little if the app is killed. */
            val SAVE_INTERVAL = 30.seconds
        }
    }
