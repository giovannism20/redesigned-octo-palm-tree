package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.playback.voice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class RoutingSpeechEngineTest {
    private val phone = RecordingEngine(listOf(voice(id = "system:com.example.tts:pt-br-x-1")))
    private val neural = RecordingEngine(listOf(voice(id = "neural:dora")))
    private val engine = RoutingSpeechEngine(phone, neural)

    @Test
    fun `offers the neural voices first, then the phone's`() {
        assertThat(runBlocking { engine.voices() }.map { it.id })
            .containsExactly("neural:dora", "system:com.example.tts:pt-br-x-1")
            .inOrder()
    }

    @Test
    fun `sends each voice to its engine`() {
        runBlocking {
            engine.synthesize("Olá.", voice(id = "neural:dora"), File("a.wav"))
            engine.synthesize("Oi.", voice(id = "system:com.example.tts:pt-br-x-1"), File("b.wav"))
        }

        assertThat(neural.spoken).containsExactly("Olá.")
        assertThat(phone.spoken).containsExactly("Oi.")
    }

    private class RecordingEngine(
        private val offered: List<Voice>,
    ) : SpeechEngine {
        val spoken = mutableListOf<String>()

        override suspend fun voices(): List<Voice> = offered

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            output: File,
            speed: Float,
        ): SynthesisResult {
            spoken += text
            return SynthesisResult.Rendered(1)
        }
    }
}
