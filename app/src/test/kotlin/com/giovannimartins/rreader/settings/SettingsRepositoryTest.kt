package com.giovannimartins.rreader.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `stores the default voice and clamps the speed`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = SettingsRepository(dataStore())

            assertThat(repository.settings.first()).isEqualTo(AppSettings())

            repository.setDefaultVoice("system:engine:pt-br-x-afs-local")
            repository.setPlaybackSpeed(5f)

            val settings = repository.settings.first()
            assertThat(settings.defaultVoiceId).isEqualTo("system:engine:pt-br-x-afs-local")
            assertThat(settings.playbackSpeed).isEqualTo(AppSettings.SPEED_RANGE.endInclusive)
        }

    @Test
    fun `keeps the reader text size within its range`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = SettingsRepository(dataStore())

            repository.setReaderTextScale(1.3f)
            assertThat(repository.settings.first().readerTextScale).isEqualTo(1.3f)

            repository.setReaderTextScale(9f)
            assertThat(repository.settings.first().readerTextScale).isEqualTo(AppSettings.TEXT_SCALE_RANGE.endInclusive)
            repository.setReaderTextScale(0.1f)
            assertThat(repository.settings.first().readerTextScale).isEqualTo(AppSettings.TEXT_SCALE_RANGE.start)
        }

    @Test
    fun `the appearance starts dark, at the normal size, with the app's typeface`() =
        runTest(UnconfinedTestDispatcher()) {
            val appearance = SettingsRepository(dataStore()).appearance.first()

            assertThat(appearance).isEqualTo(Appearance(TextSize.NORMAL, ThemeChoice.DARK, ReadingFont.ATKINSON))
        }

    @Test
    fun `every text size, theme and reading font is saved and read back`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = dataStore()
            val repository = SettingsRepository(dataStore)

            TextSize.entries.forEach { size ->
                repository.setTextSize(size)
                assertThat(repository.appearance.first().textSize).isEqualTo(size)
            }
            ThemeChoice.entries.forEach { theme ->
                repository.setTheme(theme)
                assertThat(repository.appearance.first().theme).isEqualTo(theme)
            }
            ReadingFont.entries.forEach { font ->
                repository.setReadingFont(font)
                assertThat(repository.appearance.first().readingFont).isEqualTo(font)
            }
            // Another repository over the same settings reads the same choices back.
            assertThat(SettingsRepository(dataStore).appearance.first())
                .isEqualTo(Appearance(TextSize.LARGEST, ThemeChoice.PHONE, ReadingFont.LITERATA))
        }

    @Test
    fun `choices are saved under keys that do not depend on the code's names`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = dataStore()
            val repository = SettingsRepository(dataStore)

            repository.setTextSize(TextSize.LARGER)
            repository.setTheme(ThemeChoice.LIGHT)
            repository.setReadingFont(ReadingFont.LITERATA)

            val saved = dataStore.data.first()
            assertThat(saved[stringPreferencesKey("text_size")]).isEqualTo("larger")
            assertThat(saved[stringPreferencesKey("theme")]).isEqualTo("light")
            assertThat(saved[stringPreferencesKey("reading_font")]).isEqualTo("literata")
        }

    @Test
    fun `unknown or damaged values fall back to the defaults`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = dataStore()
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("text_size")] = "enormous"
                prefs[stringPreferencesKey("theme")] = ""
                prefs[intPreferencesKey("reading_font")] = 2
                prefs[stringPreferencesKey("playback_speed")] = "fast"
                prefs[floatPreferencesKey("reader_text_scale")] = Float.NaN
                prefs[intPreferencesKey("default_voice_id")] = 7
            }

            val settings = SettingsRepository(dataStore).settings.first()

            assertThat(settings).isEqualTo(AppSettings())
        }

    @Test
    fun `the appearance is told again only when it changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = SettingsRepository(dataStore())
            val seen = async { repository.appearance.take(2).toList() }

            repository.setPlaybackSpeed(1.5f)
            repository.setTheme(ThemeChoice.LIGHT)

            assertThat(seen.await()).containsExactly(Appearance(), Appearance(theme = ThemeChoice.LIGHT)).inOrder()
        }

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }
}
