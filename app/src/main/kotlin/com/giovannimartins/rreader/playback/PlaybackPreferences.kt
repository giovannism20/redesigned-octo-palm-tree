package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The listener's choices that shape playback: which voice reads and how fast. */
@Singleton
class PlaybackPreferences
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val engine: SpeechEngine,
    ) {
        /** The installed voice for a book whose own voice is [bookVoiceId] (see [VoiceSelector.select]). */
        suspend fun voiceFor(bookVoiceId: String?): Voice? =
            VoiceSelector.select(bookVoiceId, settings.settings.first().defaultVoiceId, engine.voices())

        /** The phone's own Portuguese voice, or null when the phone has none (see [VoiceSelector.phoneVoice]). */
        suspend fun phoneVoice(): Voice? = VoiceSelector.phoneVoice(engine.voices())

        suspend fun speed(): Float = settings.settings.first().playbackSpeed

        suspend fun saveSpeed(speed: Float) = settings.setPlaybackSpeed(speed)
    }
