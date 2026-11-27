package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.textkit.model.TextRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/** One segment to render: [text] is the speech form of [range] of chapter [chapterIndex]. */
data class SegmentRequest(
    val bookId: String,
    val voice: Voice,
    val chapterIndex: Int,
    val range: TextRange,
    val text: String,
)

/** Outcome of [SegmentRenderer.render]. */
sealed interface SegmentAudio {
    /** [file] is a WAV file of [durationMillis] at 1.0×. */
    data class Ready(
        val file: File,
        val durationMillis: Long,
    ) : SegmentAudio

    data class Failed(
        val reason: SynthesisFailure,
    ) : SegmentAudio
}

/**
 * Renders segments with the [SpeechEngine] into the [AudioCache] (ADR 0003). A segment already in the cache
 * with a readable WAV duration is reused, and every rendered or reused segment teaches the
 * [SpeakingRateTracker] how fast the voice speaks. Audio is written to a temporary file and moved into place
 * only when complete, so an interrupted render never looks like a cache hit. Every [TRIM_INTERVAL] renders the
 * cache is trimmed to [MAX_CACHE_BYTES], keeping the most recently rendered segments.
 */
@Singleton
class SegmentRenderer
    @Inject
    constructor(
        private val engine: SpeechEngine,
        private val cache: AudioCache,
        private val rates: SpeakingRateTracker,
        private val clock: Clock,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val rendersSinceTrim = AtomicInteger(TRIM_INTERVAL)
        private val recent = ArrayDeque<File>()

        /** The audio of [request], from the cache or freshly synthesized. */
        suspend fun render(request: SegmentRequest): SegmentAudio {
            val target =
                cache.segmentFile(
                    request.bookId,
                    request.voice.id,
                    request.chapterIndex,
                    request.range.start,
                    request.range.end,
                )
            val audio = cached(target) ?: synthesize(request, target)
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

        private suspend fun cached(target: File): SegmentAudio.Ready? =
            withContext(io) {
                WavInfo.durationMillis(target)?.takeIf { it > 0 }?.let { duration ->
                    // Touch the file so the least-recently-used trimming keeps audio that is still being played.
                    target.setLastModified(clock.millis())
                    SegmentAudio.Ready(target, duration)
                }
            }

        private suspend fun synthesize(
            request: SegmentRequest,
            target: File,
        ): SegmentAudio {
            val partial = File(target.parentFile, target.name + PARTIAL_SUFFIX)
            withContext(io) { partial.delete() }
            val result = engine.synthesize(request.text, request.voice, partial)
            return withContext(io) {
                when {
                    result is SynthesisResult.Failed -> {
                        partial.delete()
                        SegmentAudio.Failed(result.reason)
                    }

                    result is SynthesisResult.Rendered && partial.renameTo(target) -> {
                        SegmentAudio.Ready(target, result.durationMillis)
                    }

                    else -> {
                        partial.delete()
                        SegmentAudio.Failed(SynthesisFailure.RENDER_ERROR)
                    }
                }
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
