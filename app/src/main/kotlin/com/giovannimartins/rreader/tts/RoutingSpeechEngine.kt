package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.tts.neural.SherpaOnnxEngine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [SpeechEngine] the app renders with: neural voices (`neural:` ids) go to [SherpaOnnxEngine], every other voice
 * to the phone's engines ([SystemTtsEngine]). The installed neural voices come first, most recommended first.
 */
@Singleton
class RoutingSpeechEngine internal constructor(
    private val system: SpeechEngine,
    private val neural: SpeechEngine,
) : SpeechEngine {
    @Inject
    constructor(
        system: SystemTtsEngine,
        neural: SherpaOnnxEngine,
    ) : this(system as SpeechEngine, neural as SpeechEngine)

    override suspend fun voices(): List<Voice> = neural.voices() + system.voices()

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
        speed: Float,
    ): SynthesisResult =
        if (VoiceIds.neuralKey(voice.id) != null) {
            neural.synthesize(text, voice, output, speed)
        } else {
            system.synthesize(text, voice, output, speed)
        }
}
