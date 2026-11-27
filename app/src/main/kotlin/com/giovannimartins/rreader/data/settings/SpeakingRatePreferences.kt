package com.giovannimartins.rreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.SpeakingRatesData
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.tts.SpeakingRateStore
import com.giovannimartins.rreader.tts.SpeakingRates
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpeakingRateStore] in its own preferences file: learned values, not choices the listener made, so they stay
 * apart from [SettingsRepository]. One key per voice (`rate:<voice id>`, characters per second) and the last
 * voice that read. Values that are not a plausible rate are ignored.
 */
@Singleton
class SpeakingRatePreferences
    @Inject
    constructor(
        @SpeakingRatesData private val dataStore: DataStore<Preferences>,
    ) : SpeakingRateStore {
        override suspend fun load(): SpeakingRates =
            try {
                read(dataStore.data.first())
            } catch (e: IOException) {
                Diagnostics.recordHandled("load speaking rates", e)
                SpeakingRates()
            }

        override suspend fun save(rates: SpeakingRates) {
            try {
                dataStore.edit { prefs ->
                    rates.byVoice.forEach { (voiceId, rate) -> prefs[rateKey(voiceId)] = rate.charsPerSecond }
                    rates.lastVoiceId?.let { prefs[LAST_VOICE] = it }
                }
            } catch (e: IOException) {
                Diagnostics.recordHandled("save speaking rates", e)
            }
        }

        private fun read(prefs: Preferences): SpeakingRates {
            val rates =
                prefs.asMap().entries.mapNotNull { (key, value) ->
                    val voiceId = key.name.takeIf { it.startsWith(RATE_PREFIX) }?.removePrefix(RATE_PREFIX)
                    val charsPerSecond = (value as? Double)?.takeIf { it.isFinite() && it in PLAUSIBLE_RATES }
                    if (voiceId.isNullOrEmpty() ||
                        charsPerSecond == null
                    ) {
                        null
                    } else {
                        voiceId to SpeakingRate(charsPerSecond)
                    }
                }
            return SpeakingRates(rates.toMap(), prefs[LAST_VOICE])
        }

        private fun rateKey(voiceId: String) = doublePreferencesKey(RATE_PREFIX + voiceId)

        private companion object {
            const val RATE_PREFIX = "rate:"
            val LAST_VOICE = stringPreferencesKey("last_voice_id")

            /** The rates [SpeakingRate.updatedWith] can produce. */
            val PLAUSIBLE_RATES = 1.0..100.0
        }
    }
