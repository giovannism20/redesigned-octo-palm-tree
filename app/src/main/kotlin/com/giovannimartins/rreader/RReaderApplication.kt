package com.giovannimartins.rreader

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application entry point; hosts the Hilt graph and configures WorkManager with Hilt workers. */
@HiltAndroidApp
class RReaderApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(workManagerLogLevel(BuildConfig.DEBUG))
                .build()

    companion object {
        /**
         * What WorkManager logs: from information up in debug builds, errors only in release builds (SR-8). At the
         * information level it logs every job's tags and outcome, which the release build must not leave in the log.
         */
        internal fun workManagerLogLevel(debug: Boolean): Int = if (debug) Log.INFO else Log.ERROR
    }
}
