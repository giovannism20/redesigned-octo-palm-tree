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
 * it again later, resuming from the pages already recognized. An import started [MAX_RUN_ATTEMPTS] times without
 * ending is given up as too complex: a file that makes the process die (in native code, out of reach of any catch)
 * would otherwise be retried forever.
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
            val bookId = inputData.getString(KEY_BOOK_ID)
            return when {
                bookId == null -> {
                    Result.failure()
                }

                runAttemptCount >= MAX_RUN_ATTEMPTS -> {
                    processor.abandon(bookId)
                    Result.failure()
                }

                else -> {
                    import(bookId)
                    Result.success()
                }
            }
        }

        private suspend fun import(bookId: String) {
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
        }

        private suspend fun report(
            bookId: String,
            progress: ImportProgress,
        ) {
            attempt("publish import progress") { setProgress(ImportProgressData.write(bookId, progress)) }
            if (!foregroundRefused) {
                foregroundRefused =
                    !attempt("run import in foreground") {
                        setForeground(notifications.foregroundInfo(bookId, progress))
                    }
            }
        }

        /** Runs [block] and tells whether it worked; a failure is recorded instead of stopping the import. */
        @Suppress("TooGenericExceptionCaught") // The platform refuses foreground services in several ways.
        private suspend fun attempt(
            where: String,
            block: suspend () -> Unit,
        ): Boolean =
            try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.recordHandled(where, e)
                false
            }

        companion object {
            const val KEY_BOOK_ID = "book_id"

            /**
             * Runs of one import, the first included, before it is given up. Text recognition of a long scan
             * legitimately runs several times when the system stops it (ADR 0015).
             */
            const val MAX_RUN_ATTEMPTS: Int = 10

            fun inputFor(bookId: String) = workDataOf(KEY_BOOK_ID to bookId)

            fun uniqueName(bookId: String) = "import-$bookId"
        }
    }
