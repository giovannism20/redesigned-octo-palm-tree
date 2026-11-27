package com.giovannimartins.rreader.work

import androidx.work.CoroutineWorker
import androidx.work.Data
import com.giovannimartins.rreader.Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [block] and publishes as this job's progress the values it reports, from any thread. Reports are conflated
 * and a value equal to the previous one is dropped, so a fast download does not flood WorkManager; the last value
 * reported is always published before this returns. A progress update WorkManager fails to store never stops the job.
 */
internal suspend fun <T, R> CoroutineWorker.reportingProgress(
    toData: (T) -> Data,
    block: suspend (report: (T) -> Unit) -> R,
): R {
    val reports = Channel<T>(Channel.CONFLATED)
    return coroutineScope {
        launch { for (value in reports) publish(toData(value)) }
        var last: Any? = NOTHING
        try {
            block { value ->
                if (value != last) {
                    last = value
                    reports.trySend(value)
                }
            }
        } finally {
            reports.close()
        }
    }
}

@Suppress("TooGenericExceptionCaught") // WorkManager fails progress updates in several ways; none must stop the job.
private suspend fun CoroutineWorker.publish(progress: Data) {
    try {
        setProgress(progress)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Diagnostics.recordHandled("publish job progress", e)
    }
}

/** Marks "nothing reported yet", which no report equals. */
private val NOTHING = Any()
