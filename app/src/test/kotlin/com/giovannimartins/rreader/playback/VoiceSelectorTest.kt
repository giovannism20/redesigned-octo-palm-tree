package com.giovannimartins.rreader.playback

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
    fun `no offline Portuguese voice means there is no voice`() {
        assertThat(VoiceSelector.select(null, null, listOf(english, brazilOnline))).isNull()
        assertThat(VoiceSelector.select(null, null, emptyList())).isNull()
    }
}
