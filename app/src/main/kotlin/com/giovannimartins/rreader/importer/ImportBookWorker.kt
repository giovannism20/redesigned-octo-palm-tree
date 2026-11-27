package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.giovannimartins.rreader.Diagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Background job that parses one imported book; survives process death.
 *
 * Stages that take long report [ImportProgress], which the job publishes through WorkManager for the library. While
 * text recognition runs, the job also asks to run in a `dataSync` foreground service with a progress notification
 * (ADR 0015), so that the system lets it run for long. When the platform refuses, for example because the job started
 * while the app is in the background, the import goes on as a normal job: the system may stop it, and WorkManager runs
 * it again later, resuming from the pages already recognized.
 */
@HiltWorker
class ImportBookWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val processor: BookImportProcessor,
        private val notifications: ImportNotifications,
    ) : CoroutineWorker(context, params) {
        private var foregroundRefused = false

        override suspend fun doWork(): Result {
            val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
            // Parsing reports from its own thread; the latest report is published, and the last one always is.
            val reports = Channel<ImportProgress>(Channel.CONFLATED)
            coroutineScope {
                launch { for (progress in reports) report(bookId, progress) }
                try {
                    processor.process(bookId) { reports.trySend(it) }
                } finally {
                    reports.close()
                }
            }
            return Result.success()
        }

        private suspend fun report(
            bookId: String,
            progress: ImportProgress,
        ) {
            setProgress(ImportProgressData.write(bookId, progress))
            if (!foregroundRefused) runInForeground(bookId, progress)
        }

        @Suppress("TooGenericExceptionCaught") // The platform refuses foreground services in several ways.
        private suspend fun runInForeground(
            bookId: String,
            progress: ImportProgress,
        ) {
            try {
                setForeground(notifications.foregroundInfo(bookId, progress))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                foregroundRefused = true
                Diagnostics.recordHandled("run import in foreground", e)
            }
        }

        companion object {
            const val KEY_BOOK_ID = "book_id"

            fun inputFor(bookId: String) = workDataOf(KEY_BOOK_ID to bookId)

            fun uniqueName(bookId: String) = "import-$bookId"
        }
    }
