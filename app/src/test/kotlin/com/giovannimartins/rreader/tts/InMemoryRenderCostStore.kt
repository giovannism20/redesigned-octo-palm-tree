package com.giovannimartins.rreader.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A [RenderCostStore] in memory: what was saved last. */
internal class InMemoryRenderCostStore(
    var saved: Map<String, Double> = emptyMap(),
) : RenderCostStore {
    override suspend fun load(): Map<String, Double> = saved

    override suspend fun save(costs: Map<String, Double>) {
        saved = costs
    }
}

/** A [RenderSpeedTracker] that starts with nothing measured and keeps what it learns in memory. */
internal fun testRenderSpeeds(
    store: RenderCostStore = InMemoryRenderCostStore(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
): RenderSpeedTracker = RenderSpeedTracker(store, scope)
