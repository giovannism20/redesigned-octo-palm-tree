package com.giovannimartins.rreader

import android.util.Log
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RReaderApplicationTest {
    @Test
    fun `SR-8 WorkManager logs only errors in release builds, where job tags must not reach the log`() {
        assertThat(RReaderApplication.workManagerLogLevel(debug = false)).isEqualTo(Log.ERROR)
        assertThat(RReaderApplication.workManagerLogLevel(debug = true)).isEqualTo(Log.INFO)
    }
}
