package com.giovannimartins.rreader.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class WavInfoTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `reads the duration of a PCM WAV`() {
        // 24 kHz, 16-bit mono = 48,000 bytes per second; 72,000 bytes = 1.5 s.
        val file = wav(sampleRate = 24_000, dataBytes = 72_000)

        assertThat(WavInfo.durationMillis(file)).isEqualTo(1_500)
    }

    @Test
    fun `falls back to the file length when the data size is unknown`() {
        val file = wav(sampleRate = 24_000, dataBytes = 48_000, declaredDataSize = 0)

        assertThat(WavInfo.durationMillis(file)).isEqualTo(1_000)
    }

    @Test
    fun `returns null for files that are not WAV`() {
        val file = folder.newFile("x.wav").apply { writeText("not audio at all") }

        assertThat(WavInfo.durationMillis(file)).isNull()
        assertThat(WavInfo.durationMillis(folder.newFile("empty.wav"))).isNull()
    }

    private fun wav(
        sampleRate: Int,
        dataBytes: Int,
        declaredDataSize: Int = dataBytes,
    ): File {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(intLe(36 + dataBytes))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intLe(16))
        out.write(shortLe(1))
        out.write(shortLe(channels))
        out.write(intLe(sampleRate))
        out.write(intLe(byteRate))
        out.write(shortLe(channels * bitsPerSample / 8))
        out.write(shortLe(bitsPerSample))
        out.write("data".toByteArray())
        out.write(intLe(declaredDataSize))
        out.write(ByteArray(dataBytes))
        return folder.newFile().apply { writeBytes(out.toByteArray()) }
    }

    private fun intLe(value: Int) =
        byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (
                value shr
                    24
            ).toByte(),
        )

    private fun shortLe(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())
}
