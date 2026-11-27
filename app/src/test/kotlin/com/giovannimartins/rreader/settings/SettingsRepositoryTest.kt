package com.giovannimartins.rreader.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
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
            val repository = repository(File(folder.root, "settings.preferences_pb"))

            assertThat(repository.settings.first()).isEqualTo(AppSettings())

            repository.setDefaultVoice("system:engine:pt-br-x-afs-local")
            repository.setPlaybackSpeed(5f)

            val settings = repository.settings.first()
            assertThat(settings.defaultVoiceId).isEqualTo("system:engine:pt-br-x-afs-local")
            assertThat(settings.playbackSpeed).isEqualTo(AppSettings.SPEED_RANGE.endInclusive)
        }

    private fun TestScope.repository(file: File) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
}
