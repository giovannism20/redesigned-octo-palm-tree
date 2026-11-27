package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.VoiceIds

/** Chooses the voice that reads a book. */
internal object VoiceSelector {
    private const val PORTUGUESE = "pt"
    private const val BRAZIL = "BR"

    /**
     * The book's own voice when it is installed, else the default voice from the settings when it is installed, else
     * the phone's voice ([phoneVoice]), else any offline Portuguese voice (a neural one when the phone has none).
     * [VoiceIds.PHONE], as the book's or the default voice, means the phone's voice. So a neural voice that was
     * deleted gives way to the default voice, and then to the phone's. Voices the user chose explicitly are honoured
     * even when they need the network; automatic choices are offline only (ADR 0002). Null when no voice qualifies.
     *
     * @param installed every installed voice: the neural ones, then the phone's in their engines' order.
     */
    fun select(
        bookVoiceId: String?,
        defaultVoiceId: String?,
        installed: List<Voice>,
    ): Voice? =
        chosen(bookVoiceId, installed)
            ?: chosen(defaultVoiceId, installed)
            ?: phoneVoice(installed)
            ?: offlinePortuguese(installed).firstOrNull()

    /**
     * The phone's own voice: the first offline Portuguese voice of the phone's engines, preferring Brazilian
     * Portuguese; null when the phone has none.
     */
    fun phoneVoice(installed: List<Voice>): Voice? =
        offlinePortuguese(installed).firstOrNull { VoiceIds.isSystem(it.id) }

    private fun chosen(
        voiceId: String?,
        installed: List<Voice>,
    ): Voice? =
        when (voiceId) {
            null -> null
            VoiceIds.PHONE -> phoneVoice(installed)
            else -> installed.firstOrNull { it.id == voiceId }
        }

    private fun offlinePortuguese(installed: List<Voice>): List<Voice> =
        installed
            .filter { !it.requiresNetwork && it.locale.language == PORTUGUESE }
            .sortedByDescending { it.locale.country == BRAZIL }
}
