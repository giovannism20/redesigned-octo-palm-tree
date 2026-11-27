package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Background job that parses one imported book; survives process death. */
@HiltWorker
class ImportBookWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val processor: BookImportProcessor,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
            processor.process(bookId)
            return Result.success()
        }

        companion object {
            const val KEY_BOOK_ID = "book_id"

            fun inputFor(bookId: String) = workDataOf(KEY_BOOK_ID to bookId)

            fun uniqueName(bookId: String) = "import-$bookId"
        }
    }
