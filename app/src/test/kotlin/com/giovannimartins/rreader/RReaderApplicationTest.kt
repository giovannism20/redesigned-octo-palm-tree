package com.giovannimartins.rreader

import android.content.ComponentCallbacks2
import android.util.Log
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RReaderApplicationTest {
    @Test
    fun `SR-8 WorkManager logs only errors in release builds, where job tags must not reach the log`() {
        assertThat(RReaderApplication.workManagerLogLevel(debug = false)).isEqualTo(Log.ERROR)
        assertThat(RReaderApplication.workManagerLogLevel(debug = true)).isEqualTo(Log.INFO)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the neural model is freed when the app is in the background or memory runs low, not for a hidden screen`() {
        val freed =
            listOf(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            )
        val kept = listOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE, ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertThat(freed.filterNot(RReaderApplication::releasesModelsOn)).isEmpty()
        assertThat(kept.filter(RReaderApplication::releasesModelsOn)).isEmpty()
    }
}
