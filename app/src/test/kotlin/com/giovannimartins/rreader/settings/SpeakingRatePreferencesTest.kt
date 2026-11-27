package com.giovannimartins.rreader.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.giovannimartins.rreader.data.settings.SpeakingRatePreferences
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.tts.SpeakingRates
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingRatePreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `saved rates and the last voice come back`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = SpeakingRatePreferences(dataStore())
            val rates =
                SpeakingRates(
                    mapOf(
                        "system:engine:pt-br-x-afs" to SpeakingRate(15.25),
                        "system:other:pt-pt" to SpeakingRate(13.0),
                    ),
                    lastVoiceId = "system:engine:pt-br-x-afs",
                )

            assertThat(store.load()).isEqualTo(SpeakingRates())
            store.save(rates)

            assertThat(store.load()).isEqualTo(rates)
        }

    @Test
    fun `values that are not a plausible rate are ignored`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = dataStore()
            dataStore.edit {
                it[doublePreferencesKey("rate:zero")] = 0.0
                it[doublePreferencesKey("rate:nan")] = Double.NaN
                it[doublePreferencesKey("rate:")] = 14.0
                it[doublePreferencesKey("unrelated")] = 14.0
            }

            assertThat(SpeakingRatePreferences(dataStore).load()).isEqualTo(SpeakingRates())
        }

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
        ) { File(folder.root, "speaking_rates.preferences_pb") }
}
