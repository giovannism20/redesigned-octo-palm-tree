package com.giovannimartins.rreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.SpeakingRatesData
import com.giovannimartins.rreader.tts.RenderCost
import com.giovannimartins.rreader.tts.RenderCostStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RenderCostStore] beside the learned speaking rates ([SpeakingRatePreferences]), in the same preferences file: one
 * key per voice (`render:<voice id>`, seconds of rendering per second of speech). Values that are not a plausible
 * cost are ignored.
 */
@Singleton
class RenderCostPreferences
    @Inject
    constructor(
        @SpeakingRatesData private val dataStore: DataStore<Preferences>,
    ) : RenderCostStore {
        override suspend fun load(): Map<String, Double> =
            try {
                dataStore.data
                    .first()
                    .asMap()
                    .entries
                    .mapNotNull { (key, value) ->
                        val voiceId = key.name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
                        val cost = (value as? Double)?.takeIf { it.isFinite() && it in RenderCost.PLAUSIBLE }
                        if (voiceId.isNullOrEmpty() || cost == null) null else voiceId to cost
                    }.toMap()
            } catch (e: IOException) {
                Diagnostics.recordHandled("load render costs", e)
                emptyMap()
            }

        override suspend fun save(costs: Map<String, Double>) {
            try {
                dataStore.edit { prefs ->
                    costs.forEach { (voiceId, cost) -> prefs[doublePreferencesKey(PREFIX + voiceId)] = cost }
                }
            } catch (e: IOException) {
                Diagnostics.recordHandled("save render costs", e)
            }
        }

        private companion object {
            const val PREFIX = "render:"
        }
    }
