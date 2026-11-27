package com.giovannimartins.rreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User preferences that apply to every book. */
data class AppSettings(
    /** Voice used when a book has no voice of its own; null until the user picks one. */
    val defaultVoiceId: String? = null,
    /** Playback speed multiplier, see [SPEED_RANGE]. */
    val playbackSpeed: Float = DEFAULT_SPEED,
    /** Multiplier for the follow-along reader text size. */
    val readerTextScale: Float = 1f,
) {
    companion object {
        const val DEFAULT_SPEED = 1f
        val SPEED_RANGE = 0.75f..2f
    }
}

@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> =
            dataStore.data.map { prefs ->
                AppSettings(
                    defaultVoiceId = prefs[DEFAULT_VOICE],
                    playbackSpeed = (prefs[SPEED] ?: AppSettings.DEFAULT_SPEED).coerceIn(AppSettings.SPEED_RANGE),
                    readerTextScale = prefs[TEXT_SCALE] ?: 1f,
                )
            }

        suspend fun setDefaultVoice(voiceId: String) {
            dataStore.edit { it[DEFAULT_VOICE] = voiceId }
        }

        suspend fun setPlaybackSpeed(speed: Float) {
            dataStore.edit { it[SPEED] = speed.coerceIn(AppSettings.SPEED_RANGE) }
        }

        suspend fun setReaderTextScale(scale: Float) {
            dataStore.edit { it[TEXT_SCALE] = scale }
        }

        private companion object {
            val DEFAULT_VOICE = stringPreferencesKey("default_voice_id")
            val SPEED = floatPreferencesKey("playback_speed")
            val TEXT_SCALE = floatPreferencesKey("reader_text_scale")
        }
    }
