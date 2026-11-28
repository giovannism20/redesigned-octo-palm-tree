package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * How long a voice takes on this phone to render one second of speech (at 1.0×), in seconds: 0.2 renders five times
 * faster than real time, 1.5 cannot keep up even at 1.0×. Moves by an exponential moving average of the segments
 * rendered; [samples] counts them, up to [TRUSTED_SAMPLES].
 */
data class RenderCost(
    val secondsPerSecond: Double,
    val samples: Int = 1,
) {
    /** This cost with one more segment: [speechMillis] of speech (at 1.0×) took [renderMillis] to render. */
    fun updatedWith(
        renderMillis: Long,
        speechMillis: Long,
    ): RenderCost =
        RenderCost(
            secondsPerSecond = secondsPerSecond + WEIGHT * (renderMillis.toDouble() / speechMillis - secondsPerSecond),
            samples = (samples + 1).coerceAtMost(TRUSTED_SAMPLES),
        )

    /** Whether enough segments were measured to tell whether the voice keeps up. */
    val isKnown: Boolean get() = samples >= TRUSTED_SAMPLES

    /** Whether rendering stays ahead of listening at [speed], with a margin for a busy or warm phone. */
    fun keepsUpAt(speed: Float): Boolean = secondsPerSecond * speed <= KEEP_UP_LIMIT

    companion object {
        /** Segments measured before the cost is trusted; costs restored from earlier sessions are trusted. */
        const val TRUSTED_SAMPLES = 3

        /** The share of the render time listening may take up: above it, the phone falls behind sooner or later. */
        const val KEEP_UP_LIMIT = 0.9

        /** The weight of the newest segment. */
        private const val WEIGHT = 0.3

        /** The costs a phone can plausibly have; others are not restored. */
        val PLAUSIBLE: ClosedFloatingPointRange<Double> = 0.001..100.0
    }
}

/** Keeps learned render costs across app restarts. */
interface RenderCostStore {
    /** The costs saved before, by voice id; empty when nothing was saved or the store cannot be read. */
    suspend fun load(): Map<String, Double>

    suspend fun save(costs: Map<String, Double>)
}

/**
 * Learns, from the segments a voice renders, whether it keeps up with listening on this phone ([RenderCost]), so the
 * player can say why it pauses and the voices tab can point out a voice that is slow here. Only the time the voice
 * itself took is counted (engines report it; loading a model is left out); short segments are ignored, their timing
 * being mostly fixed costs.
 *
 * Costs survive restarts in the [store]: they are loaded once in [scope] (costs measured since win) and saved at most
 * every [SAVE_INTERVAL], the first time right away. Safe to use from any thread.
 */
@Singleton
class RenderSpeedTracker
    @Inject
    constructor(
        private val store: RenderCostStore,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val state = MutableStateFlow<Map<String, RenderCost>>(emptyMap())
        private val changed = Channel<Unit>(Channel.CONFLATED)

        init {
            scope.launch {
                val saved =
                    store.load().mapValues { (_, cost) -> RenderCost(cost, RenderCost.TRUSTED_SAMPLES) }
                state.update { saved + it }
                changed.consumeEach {
                    store.save(state.value.mapValues { (_, cost) -> cost.secondsPerSecond })
                    delay(SAVE_INTERVAL)
                }
            }
        }

        /** Every measured voice's render cost on this phone, by voice id. */
        val costs: StateFlow<Map<String, RenderCost>> = state.asStateFlow()

        /**
         * Blends one rendered segment into [voiceId]'s cost: [speechMillis] of speech (at 1.0×) took [renderMillis] to
         * render.
         */
        fun record(
            voiceId: String,
            renderMillis: Long,
            speechMillis: Long,
        ) {
            if (speechMillis < MIN_SAMPLE_MILLIS || renderMillis < 0) return
            state.update { costs ->
                val cost =
                    costs[voiceId]?.updatedWith(renderMillis, speechMillis)
                        ?: RenderCost(renderMillis.toDouble() / speechMillis)
                costs + (voiceId to cost)
            }
            changed.trySend(Unit)
        }

        /**
         * False when [voiceId] was measured to render slower than listening at [speed] on this phone, true when it
         * keeps up, null while that is not known.
         */
        fun keepsUp(
            voiceId: String,
            speed: Float,
        ): Boolean? = state.value[voiceId]?.takeIf(RenderCost::isKnown)?.keepsUpAt(speed)

        private companion object {
            const val MIN_SAMPLE_MILLIS = 2_000L

            /** Costs move slowly, so saving twice a minute at most loses little if the app is killed. */
            val SAVE_INTERVAL = 30.seconds
        }
    }
