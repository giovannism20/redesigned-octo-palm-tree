package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.tts.Voice

/** Chooses the voice that reads a book. */
internal object VoiceSelector {
    private const val PORTUGUESE = "pt"
    private const val BRAZIL = "BR"

    /**
     * The book's own voice when it is installed, else the default voice from the settings when it is
     * installed, else the first offline Portuguese voice, preferring Brazilian Portuguese. Voices the user
     * chose explicitly are honoured even when they need the network; automatic choices are offline only
     * (ADR 0002). Null when no voice qualifies.
     *
     * @param installed the engine's voices in its preference order (offline first, then quality).
     */
    fun select(
        bookVoiceId: String?,
        defaultVoiceId: String?,
        installed: List<Voice>,
    ): Voice? =
        installed.firstOrNull { it.id == bookVoiceId }
            ?: installed.firstOrNull { it.id == defaultVoiceId }
            ?: installed
                .filter { !it.requiresNetwork && it.locale.language == PORTUGUESE }
                .sortedByDescending { it.locale.country == BRAZIL }
                .firstOrNull()
}
