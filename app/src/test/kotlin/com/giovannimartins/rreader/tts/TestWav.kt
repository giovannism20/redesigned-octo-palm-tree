package com.giovannimartins.rreader.tts

import java.io.ByteArrayOutputStream
import java.io.File

/** Writes small 16-bit mono PCM WAV files for tests. */
object TestWav {
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1
    private const val HEADER_SIZE_AFTER_RIFF = 36
    private const val FMT_CHUNK_SIZE = 16
    private const val PCM = 1

    /** A silent WAV of [durationMillis] at [sampleRate]. */
    fun write(
        file: File,
        durationMillis: Long,
        sampleRate: Int = 8_000,
    ) {
        val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        write(file, sampleRate, dataBytes = (durationMillis * byteRate / 1_000).toInt())
    }

    /** A silent WAV with [dataBytes] of samples whose header declares [declaredDataSize]. */
    fun write(
        file: File,
        sampleRate: Int,
        dataBytes: Int,
        declaredDataSize: Int = dataBytes,
    ) {
        val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(intLe(HEADER_SIZE_AFTER_RIFF + dataBytes))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intLe(FMT_CHUNK_SIZE))
        out.write(shortLe(PCM))
        out.write(shortLe(CHANNELS))
        out.write(intLe(sampleRate))
        out.write(intLe(byteRate))
        out.write(shortLe(CHANNELS * BITS_PER_SAMPLE / 8))
        out.write(shortLe(BITS_PER_SAMPLE))
        out.write("data".toByteArray())
        out.write(intLe(declaredDataSize))
        out.write(ByteArray(dataBytes))
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }

    private fun intLe(value: Int) =
        byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun shortLe(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())
}
