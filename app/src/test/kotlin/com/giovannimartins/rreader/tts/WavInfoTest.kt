package com.giovannimartins.rreader.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavInfoTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `reads the duration of a PCM WAV`() {
        // 24 kHz, 16-bit mono = 48,000 bytes per second; 72,000 bytes = 1.5 s.
        val file = folder.newFile().also { TestWav.write(it, sampleRate = 24_000, dataBytes = 72_000) }

        assertThat(WavInfo.durationMillis(file)).isEqualTo(1_500)
    }

    @Test
    fun `falls back to the file length when the data size is unknown`() {
        val file =
            folder.newFile().also {
                TestWav.write(it, sampleRate = 24_000, dataBytes = 48_000, declaredDataSize = 0)
            }

        assertThat(WavInfo.durationMillis(file)).isEqualTo(1_000)
    }

    @Test
    fun `returns null for files that are not WAV`() {
        val file = folder.newFile("x.wav").apply { writeText("not audio at all") }

        assertThat(WavInfo.durationMillis(file)).isNull()
        assertThat(WavInfo.durationMillis(folder.newFile("empty.wav"))).isNull()
    }
}
