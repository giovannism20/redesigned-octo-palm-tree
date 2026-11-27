package com.giovannimartins.rreader.tts

import java.io.File

/** Renders text to audio files (ADR 0003). Implementations are free and on-device (ADR 0002). */
interface SpeechEngine {
    /** Voices currently installed and usable, offline ones first. */
    suspend fun voices(): List<Voice>

    /** Renders [text] spoken by [voice] into a WAV file at [output]. */
    suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
    ): SynthesisResult
}

/** Outcome of [SpeechEngine.synthesize]. */
sealed interface SynthesisResult {
    data class Rendered(
        val durationMillis: Long,
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
