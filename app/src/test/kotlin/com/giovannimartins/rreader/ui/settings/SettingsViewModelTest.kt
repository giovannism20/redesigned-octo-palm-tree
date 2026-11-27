package com.giovannimartins.rreader.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/** Under Robolectric because failures are logged with the Android log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `reads the saved appearance and applies every change at once`() =
        runTest {
            val viewModel = SettingsViewModel(SettingsRepository(dataStore()))
            assertThat(viewModel.appearance.filterNotNull().first()).isEqualTo(Appearance())

            viewModel.setTextSize(TextSize.LARGEST)
            viewModel.setTheme(ThemeChoice.LIGHT)
            viewModel.setReadingFont(ReadingFont.LITERATA)

            val changed = viewModel.appearance.first { it?.readingFont == ReadingFont.LITERATA }
            assertThat(changed).isEqualTo(Appearance(TextSize.LARGEST, ThemeChoice.LIGHT, ReadingFont.LITERATA))
        }

    @Test
    fun `settings that cannot be read give the default appearance instead of failing`() =
        runTest {
            val broken =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("unreadable") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("unwritable")
                }
            val viewModel = SettingsViewModel(SettingsRepository(broken))

            assertThat(viewModel.appearance.filterNotNull().first()).isEqualTo(Appearance())
            viewModel.setTheme(ThemeChoice.LIGHT) // Saving fails quietly; the screen keeps working.
            assertThat(viewModel.appearance.value).isEqualTo(Appearance())
        }

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }
}
