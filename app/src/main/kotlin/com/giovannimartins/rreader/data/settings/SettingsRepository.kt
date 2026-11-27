package com.giovannimartins.rreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User preferences that apply to every book. */
data class AppSettings(
    /** Voice used when a book has no voice of its own; null until the user picks one. */
    val defaultVoiceId: String? = null,
    /** Playback speed multiplier, see [SPEED_RANGE]. */
    val playbackSpeed: Float = DEFAULT_SPEED,
    /** Multiplier for the follow-along reader text size, on top of the system font scale; see [TEXT_SCALE_RANGE]. */
    val readerTextScale: Float = DEFAULT_TEXT_SCALE,
    /** Text size, theme and reading font. */
    val appearance: Appearance = Appearance(),
) {
    companion object {
        const val DEFAULT_SPEED = 1f
        val SPEED_RANGE = 0.75f..2f
        const val DEFAULT_TEXT_SCALE = 1f
        val TEXT_SCALE_RANGE = 0.85f..2f
    }
}

/**
 * The listener's settings in DataStore. Every value read is checked: a missing, unknown or damaged value (of another
 * type, or not a number) counts as not set, so the default applies instead of the app failing.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> = dataStore.data.map(::read)

        /** The listener's appearance choices; emits only when one of them changes. */
        val appearance: Flow<Appearance> = settings.map { it.appearance }.distinctUntilChanged()

        suspend fun setDefaultVoice(voiceId: String) {
            dataStore.edit { it[DEFAULT_VOICE] = voiceId }
        }

        suspend fun setPlaybackSpeed(speed: Float) {
            dataStore.edit { it[SPEED] = speed.coerceIn(AppSettings.SPEED_RANGE) }
        }

        suspend fun setReaderTextScale(scale: Float) {
            dataStore.edit { it[TEXT_SCALE] = scale.coerceIn(AppSettings.TEXT_SCALE_RANGE) }
        }

        suspend fun setTextSize(size: TextSize) = saveChoice(TEXT_SIZE, size)

        suspend fun setTheme(theme: ThemeChoice) = saveChoice(THEME, theme)

        suspend fun setReadingFont(font: ReadingFont) = saveChoice(READING_FONT, font)

        private fun read(prefs: Preferences): AppSettings {
            val defaults = Appearance()
            return AppSettings(
                defaultVoiceId = prefs.valueOf(DEFAULT_VOICE),
                playbackSpeed = prefs.numberOf(SPEED, AppSettings.DEFAULT_SPEED).coerceIn(AppSettings.SPEED_RANGE),
                readerTextScale =
                    prefs.numberOf(TEXT_SCALE, AppSettings.DEFAULT_TEXT_SCALE).coerceIn(AppSettings.TEXT_SCALE_RANGE),
                appearance =
                    Appearance(
                        textSize = TextSize.entries.byKey(prefs.valueOf(TEXT_SIZE), defaults.textSize),
                        theme = ThemeChoice.entries.byKey(prefs.valueOf(THEME), defaults.theme),
                        readingFont = ReadingFont.entries.byKey(prefs.valueOf(READING_FONT), defaults.readingFont),
                    ),
            )
        }

        private suspend fun saveChoice(
            key: Preferences.Key<String>,
            choice: StoredChoice,
        ) {
            dataStore.edit { it[key] = choice.key }
        }

        /** The value saved under [key], or null when there is none or it has another type (a damaged file). */
        private inline fun <reified T> Preferences.valueOf(key: Preferences.Key<T>): T? = asMap()[key] as? T

        /** The finite number saved under [key], or [default]. */
        private fun Preferences.numberOf(
            key: Preferences.Key<Float>,
            default: Float,
        ): Float = valueOf(key)?.takeIf { it.isFinite() } ?: default

        private companion object {
            val DEFAULT_VOICE = stringPreferencesKey("default_voice_id")
            val SPEED = floatPreferencesKey("playback_speed")
            val TEXT_SCALE = floatPreferencesKey("reader_text_scale")
            val TEXT_SIZE = stringPreferencesKey("text_size")
            val THEME = stringPreferencesKey("theme")
            val READING_FONT = stringPreferencesKey("reading_font")
        }
    }
