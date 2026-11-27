package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.tts.VoiceIds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class VoiceSelectorTest {
    private val english = voice(id = "en", locale = Locale.US)
    private val portugal = voice(id = "pt-pt", locale = Locale.forLanguageTag("pt-PT"))
    private val brazil = voice(id = "pt-br", locale = Locale.forLanguageTag("pt-BR"))
    private val brazilOnline = voice(id = "pt-br-net", locale = Locale.forLanguageTag("pt-BR"), requiresNetwork = true)
    private val installed = listOf(english, brazilOnline, portugal, brazil)

    @Test
    fun `the book's own voice wins when it is installed`() {
        assertThat(VoiceSelector.select("pt-pt", "pt-br", installed)).isEqualTo(portugal)
    }

    @Test
    fun `otherwise the default voice from the settings is used`() {
        assertThat(VoiceSelector.select(null, "pt-pt", installed)).isEqualTo(portugal)
        assertThat(VoiceSelector.select("uninstalled", "pt-pt", installed)).isEqualTo(portugal)
    }

    @Test
    fun `a voice the listener chose is honoured even when it needs the network`() {
        assertThat(VoiceSelector.select(null, "pt-br-net", installed)).isEqualTo(brazilOnline)
    }

    @Test
    fun `otherwise the first offline Portuguese voice is chosen, preferring Brazilian Portuguese`() {
        assertThat(VoiceSelector.select(null, null, installed)).isEqualTo(brazil)
        assertThat(VoiceSelector.select(null, "uninstalled", listOf(english, portugal))).isEqualTo(portugal)
    }

    @Test
    fun `the phone's voice is the best offline Portuguese voice of the phone's engines`() {
        val dora = voice(id = "neural:dora", locale = Locale.forLanguageTag("pt-BR"))
        val phoneBrazil = voice(id = "system:engine:pt-br", locale = Locale.forLanguageTag("pt-BR"))
        val phonePortugal = voice(id = "system:engine:pt-pt", locale = Locale.forLanguageTag("pt-PT"))
        val voices = listOf(dora, phonePortugal, phoneBrazil)

        assertThat(VoiceSelector.phoneVoice(voices)).isEqualTo(phoneBrazil)
        assertThat(VoiceSelector.select(VoiceIds.PHONE, "neural:dora", voices)).isEqualTo(phoneBrazil)
        assertThat(VoiceSelector.select(null, VoiceIds.PHONE, voices)).isEqualTo(phoneBrazil)
        // Nothing chosen: the phone's voice, not a neural voice that happens to be installed.
        assertThat(VoiceSelector.select(null, null, voices)).isEqualTo(phoneBrazil)
    }

    @Test
    fun `a deleted neural voice gives way to the default voice, then to the phone's`() {
        val alex = voice(id = "neural:alex", locale = Locale.forLanguageTag("pt-BR"))
        val phoneVoice = voice(id = "system:engine:pt-br", locale = Locale.forLanguageTag("pt-BR"))

        assertThat(VoiceSelector.select("neural:dora", "neural:alex", listOf(alex, phoneVoice))).isEqualTo(alex)
        assertThat(VoiceSelector.select("neural:dora", "neural:dora", listOf(alex, phoneVoice))).isEqualTo(phoneVoice)
        assertThat(VoiceSelector.select(null, "neural:dora", listOf(phoneVoice))).isEqualTo(phoneVoice)
    }

    @Test
    fun `a phone without Portuguese voices reads with an installed neural voice`() {
        val dora = voice(id = "neural:dora", locale = Locale.forLanguageTag("pt-BR"))

        assertThat(VoiceSelector.select(null, VoiceIds.PHONE, listOf(english, dora))).isEqualTo(dora)
    }

    @Test
    fun `no offline Portuguese voice means there is no voice`() {
        assertThat(VoiceSelector.select(null, null, listOf(english, brazilOnline))).isNull()
        assertThat(VoiceSelector.select(null, null, emptyList())).isNull()
    }
}
