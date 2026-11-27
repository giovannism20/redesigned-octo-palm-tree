package com.giovannimartins.rreader.importer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [ImportJobs] that only records what was queued or cancelled, for tests that do not exercise WorkManager. */
internal class RecordingImportJobs : ImportJobs {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override fun enqueue(bookId: String) {
        enqueued += bookId
    }

    override fun cancel(bookId: String) {
        cancelled += bookId
    }

    override val progress: Flow<Map<String, ImportProgress>> = MutableStateFlow(emptyMap())
}
