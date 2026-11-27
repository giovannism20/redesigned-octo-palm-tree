package com.giovannimartins.rreader.importer

import kotlinx.coroutines.flow.MutableStateFlow

/** Import jobs whose progress a test sets, and which remember what was queued and cancelled. */
internal class FakeImportJobs : ImportJobs {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override val progress = MutableStateFlow<Map<String, ImportProgress>>(emptyMap())

    override fun enqueue(bookId: String) {
        enqueued += bookId
    }

    override fun cancel(bookId: String) {
        cancelled += bookId
    }
}
