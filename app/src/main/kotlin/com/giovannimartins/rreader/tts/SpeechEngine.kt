package com.giovannimartins.rreader.tts

import java.io.File

/** Renders text to audio files (ADR 0003). Implementations are free and on-device (ADR 0002). */
interface SpeechEngine {
    /** Voices currently installed and usable, offline ones first. */
    suspend fun voices(): List<Voice>

    /**
     * Renders [text] spoken by [voice] into a WAV file at [output], at [speed] when the voice
     * [renders at speed][Voice.rendersAtSpeed] (other voices ignore it and speak at 1.0×).
     */
    suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
        speed: Float = RenderSpeeds.NORMAL,
    ): SynthesisResult
}

/** Outcome of [SpeechEngine.synthesize]. */
sealed interface SynthesisResult {
    /**
     * The audio file holds [durationMillis] of speech; the voice took [renderMillis] to speak it, model loading left
     * out, or null when the engine cannot tell.
     */
    data class Rendered(
        val durationMillis: Long,
        val renderMillis: Long? = null,
    ) : SynthesisResult

    data class Failed(
        val reason: SynthesisFailure,
    ) : SynthesisResult
}

enum class SynthesisFailure {
    ENGINE_UNAVAILABLE,
    VOICE_UNAVAILABLE,
    RENDER_ERROR,
    TIMEOUT,
}
