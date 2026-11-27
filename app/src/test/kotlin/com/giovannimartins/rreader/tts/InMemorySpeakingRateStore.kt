package com.giovannimartins.rreader.tts

/** A [SpeakingRateStore] in memory that counts its saves. */
internal class InMemorySpeakingRateStore(
    var saved: SpeakingRates = SpeakingRates(),
) : SpeakingRateStore {
    var saves = 0

    override suspend fun load(): SpeakingRates = saved

    override suspend fun save(rates: SpeakingRates) {
        saved = rates
        saves++
    }
}
