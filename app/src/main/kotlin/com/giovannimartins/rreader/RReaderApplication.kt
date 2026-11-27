package com.giovannimartins.rreader

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.giovannimartins.rreader.tts.neural.SherpaOnnxEngine
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point; hosts the Hilt graph, configures WorkManager with Hilt workers, and frees the neural voices'
 * model when the system asks for memory.
 */
@HiltAndroidApp
class RReaderApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Created only when something uses it (or memory is trimmed), since it is the largest user of memory. */
    @Inject
    lateinit var neuralVoices: Lazy<SherpaOnnxEngine>

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(workManagerLogLevel(BuildConfig.DEBUG))
                .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (releasesModelsOn(level)) neuralVoices.get().releaseIfIdle()
    }

    companion object {
        /**
         * What WorkManager logs: from information up in debug builds, errors only in release builds (SR-8). At the
         * information level it logs every job's tags and outcome, which the release build must not leave in the log.
         */
        internal fun workManagerLogLevel(debug: Boolean): Int = if (debug) Log.INFO else Log.ERROR

        /**
         * Whether trimming memory at [level] releases the neural voices' model (up to 142 MB): when the app is in the
         * background, or memory runs low while it runs (levels Android 14 no longer sends). Not when its screen is
         * only hidden: listening goes on with the screen off, and the model would be loaded again at once.
         */
        @Suppress("DEPRECATION")
        internal fun releasesModelsOn(level: Int): Boolean =
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    }
}
