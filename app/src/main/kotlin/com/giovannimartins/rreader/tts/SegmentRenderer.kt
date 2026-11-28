package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.textkit.model.TextRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * One segment to render: [text] is the speech form of [range] of chapter [chapterIndex], for listening at [speed] (a
 * voice that [renders at speed][Voice.rendersAtSpeed] speaks at it, see [RenderSpeeds]).
 */
data class SegmentRequest(
    val bookId: String,
    val voice: Voice,
    val chapterIndex: Int,
    val range: TextRange,
    val text: String,
    val speed: Float = RenderSpeeds.NORMAL,
)

/** Outcome of [SegmentRenderer.render]. */
sealed interface SegmentAudio {
    /**
     * [file] is a WAV file rendered at [speed]; [durationMillis] is how long it lasts heard at 1.0× of the voice's own
     * pace, which is the file's length times [speed].
     */
    data class Ready(
        val file: File,
        val durationMillis: Long,
        val speed: Float = RenderSpeeds.NORMAL,
    ) : SegmentAudio {
        /** The file's own length. */
        val mediaMillis: Long get() = (durationMillis / speed).roundToLong()
    }

    data class Failed(
        val reason: SynthesisFailure,
    ) : SegmentAudio
}

/**
 * Renders segments with the [SpeechEngine] into the [AudioCache] (ADR 0003). A segment already in the cache
 * with a readable WAV duration is reused, unless the voice now renders differently (its [Voice.audioKey] holds its
 * render version, so such audio is filed apart), and every rendered or reused segment teaches the
 * [SpeakingRateTracker] how fast the voice speaks, and every segment it renders teaches the [RenderSpeedTracker] how
 * fast it renders on this phone. A voice that renders at speed does so at the request's speed ([RenderSpeeds]), and
 * audio already cached at another speed is reused as it is. Audio is written to a temporary file and moved into place
 * only when complete, so an interrupted render never looks like a cache hit. Every [TRIM_INTERVAL] renders the
 * cache is trimmed to [MAX_CACHE_BYTES], keeping the most recently rendered segments.
 *
 * Listening comes first: while the player fills its render-ahead window ([inForeground]), background renders (the
 * pre-render job) wait before each segment, so the voice works for the listener first.
 */
@Singleton
class SegmentRenderer
    @Inject
    constructor(
        private val engine: SpeechEngine,
        private val cache: AudioCache,
        private val rates: SpeakingRateTracker,
        private val renderSpeeds: RenderSpeedTracker,
        private val clock: Clock,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val rendersSinceTrim = AtomicInteger(TRIM_INTERVAL)
        private val renders = AtomicInteger()
        private val recent = ArrayDeque<File>()

        /** How many foreground bursts of renders are running now. */
        private val foreground = MutableStateFlow(0)

        /** Runs [block], a burst of renders listening waits for; background renders wait until it ends. */
        suspend fun <T> inForeground(block: suspend () -> T): T {
            foreground.update { it + 1 }
            try {
                return block()
            } finally {
                foreground.update { it - 1 }
            }
        }

        /**
         * The audio of [request], from the cache or freshly synthesized. A [background] render first waits until no
         * foreground burst runs.
         */
        suspend fun render(
            request: SegmentRequest,
            background: Boolean = false,
        ): SegmentAudio {
            if (background) foreground.first { it == 0 }
            val speeds =
                if (request.voice.rendersAtSpeed) {
                    RenderSpeeds.lookupOrder(
                        request.speed,
                    )
                } else {
                    listOf(RenderSpeeds.NORMAL)
                }
            val audio =
                speeds.firstNotNullOfOrNull { speed -> cached(fileOf(request, speed), speed) }
                    ?: synthesize(request, speeds.first())
            if (audio is SegmentAudio.Ready) {
                rates.record(request.voice.id, request.range.length, audio.durationMillis.milliseconds)
                trimNowAndThen(audio.file)
            }
            return audio
        }

        /** Deletes a cached segment, for example one the player could not decode, so it is rendered again. */
        suspend fun discard(file: File) {
            withContext(io) { file.delete() }
        }

        private fun fileOf(
            request: SegmentRequest,
            speed: Float,
        ): File =
            cache.segmentFile(
                request.bookId,
                request.voice.audioKey,
                request.chapterIndex,
                request.range.start,
                request.range.end,
                speed,
            )

        private suspend fun cached(
            target: File,
            speed: Float,
        ): SegmentAudio.Ready? =
            withContext(io) {
                WavInfo.durationMillis(target)?.takeIf { it > 0 }?.let { duration ->
                    // Touch the file so the least-recently-used trimming keeps audio that is still being played.
                    target.setLastModified(clock.millis())
                    SegmentAudio.Ready(target, (duration * speed).roundToLong(), speed)
                }
            }

        private suspend fun synthesize(
            request: SegmentRequest,
            speed: Float,
        ): SegmentAudio {
            val target = fileOf(request, speed)
            // A name of its own, so two renders of the same segment (the player's and the pre-render's) never write
            // into each other's file.
            val partial = File(target.parentFile, "${target.name}.${renders.incrementAndGet()}$PARTIAL_SUFFIX")
            try {
                withContext(io) { partial.delete() }
                val result = engine.synthesize(request.text, request.voice, partial, speed)
                if (result is SynthesisResult.Rendered && result.renderMillis != null) {
                    renderSpeeds.record(
                        request.voice.id,
                        result.renderMillis,
                        (result.durationMillis * speed).roundToLong(),
                    )
                }
                return withContext(io) { adopt(result, partial, target, speed) }
            } catch (e: CancellationException) {
                // A render stopped half-way leaves nothing behind.
                partial.delete()
                throw e
            }
        }

        /**
         * Moves the [partial] file [result] wrote at [speed] into place as [target]; deletes it when the render failed.
         */
        private fun adopt(
            result: SynthesisResult,
            partial: File,
            target: File,
            speed: Float,
        ): SegmentAudio =
            when {
                result is SynthesisResult.Failed -> {
                    partial.delete()
                    SegmentAudio.Failed(result.reason)
                }

                result is SynthesisResult.Rendered && partial.renameTo(target) -> {
                    SegmentAudio.Ready(target, (result.durationMillis * speed).roundToLong(), speed)
                }

                else -> {
                    partial.delete()
                    SegmentAudio.Failed(SynthesisFailure.RENDER_ERROR)
                }
            }

        private suspend fun trimNowAndThen(rendered: File) {
            val keep =
                synchronized(recent) {
                    recent.addLast(rendered)
                    while (recent.size > KEEP_RECENT) recent.removeFirst()
                    recent.toSet()
                }
            if (rendersSinceTrim.incrementAndGet() > TRIM_INTERVAL) {
                rendersSinceTrim.set(0)
                withContext(io) { cache.trim(MAX_CACHE_BYTES, keep) }
            }
        }

        companion object {
            /** Upper bound of rendered audio kept on disk (about 1.5 hours of 24 kHz speech). */
            const val MAX_CACHE_BYTES = 300L * 1024 * 1024

            /** Renders between two cache trims; the first render after start-up also trims. */
            private const val TRIM_INTERVAL = 20

            /** Recently rendered segments that a trim never deletes: more than any render-ahead window. */
            private const val KEEP_RECENT = 64
            private const val PARTIAL_SUFFIX = ".part"
        }
    }
