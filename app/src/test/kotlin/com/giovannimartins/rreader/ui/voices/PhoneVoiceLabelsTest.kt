package com.giovannimartins.rreader.ui.voices

import com.giovannimartins.rreader.tts.Voice
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/** The owner's device test: the phone's voices all had the same name. */
class PhoneVoiceLabelsTest {
    @Test
    fun `numbers the voices of each engine, which is named in short`() {
        val voices =
            listOf(
                voice("com.google.android.tts", "Speech Services by Google", "pt-br-x-afs-local"),
                voice("com.google.android.tts", "Speech Services by Google", "pt-br-x-pte-local"),
                voice("com.samsung.SMT", "Samsung text-to-speech engine", "pt_BR_f00"),
                voice("com.google.android.tts", "Speech Services by Google", "pt-br-x-afs-network"),
            )

        val labels = PhoneVoiceLabels.of(voices).map { it.engine to it.number }

        assertThat(labels).containsExactly("Google" to 1, "Google" to 2, "Samsung" to 1, "Google" to 3).inOrder()
    }

    @Test
    fun `no two voices ever get the same label`() {
        val voices =
            listOf(
                voice("org.example.tts", "Voices", "a"),
                voice("org.example.tts", "Voices", "b"),
                voice("net.example.speech", "Voices", "a"),
                voice("com.example.reader", "Reader TTS", "a"),
            )

        val labels = PhoneVoiceLabels.of(voices).map { "${it.engine} · ${it.number}" }

        assertThat(labels).containsNoDuplicates()
        assertThat(labels).contains("Reader TTS · 1")
    }

    private fun voice(
        engine: String,
        engineLabel: String,
        name: String,
    ) = Voice(
        id = "system:$engine:$name",
        engine = engine,
        engineLabel = engineLabel,
        name = name,
        locale = Locale.forLanguageTag("pt-BR"),
        requiresNetwork = name.endsWith("network"),
        quality = 400,
    )
}
