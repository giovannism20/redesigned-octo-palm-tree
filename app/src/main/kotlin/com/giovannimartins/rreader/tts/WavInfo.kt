package com.giovannimartins.rreader.tts

import java.io.File
import java.io.RandomAccessFile

/** Reads the playing time of a PCM WAV file from its RIFF header. */
internal object WavInfo {
    private const val FOURCC_SIZE = 4
    private const val UINT32_SIZE = 4
    private const val RIFF_HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val BYTE_RATE_OFFSET_IN_FMT = 8
    private const val MILLIS_PER_SECOND = 1_000L
    private const val UNKNOWN_SIZE = 0xFFFFFFFFL
    private const val BYTE_MASK = 0xFFL
    private const val BITS_PER_BYTE = 8

    /** Duration in milliseconds, or null when the file is not a readable WAV. */
    fun durationMillis(file: File): Long? = runCatching { RandomAccessFile(file, "r").use(::readDuration) }.getOrNull()

    private fun readDuration(wav: RandomAccessFile): Long? {
        val isWave =
            wav.length() >= RIFF_HEADER_SIZE &&
                wav.readFourCc() == "RIFF" &&
                wav.skipBytes(UINT32_SIZE) == UINT32_SIZE &&
                wav.readFourCc() == "WAVE"
        var byteRate = 0L
        var duration: Long? = null
        while (isWave && duration == null && wav.filePointer + CHUNK_HEADER_SIZE <= wav.length()) {
            val id = wav.readFourCc()
            val size = wav.readUInt32()
            val dataStart = wav.filePointer
            if (id == "fmt ") {
                wav.seek(dataStart + BYTE_RATE_OFFSET_IN_FMT)
                byteRate = wav.readUInt32()
            } else if (id == "data" && byteRate > 0) {
                val available = wav.length() - dataStart
                val dataSize = if (size == 0L || size == UNKNOWN_SIZE || size > available) available else size
                duration = dataSize * MILLIS_PER_SECOND / byteRate
            }
            wav.seek(dataStart + size + (size and 1L))
        }
        return duration
    }

    private fun RandomAccessFile.readFourCc(): String {
        val bytes = ByteArray(FOURCC_SIZE)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt32(): Long {
        val bytes = ByteArray(UINT32_SIZE)
        readFully(bytes)
        return bytes.foldIndexed(0L) { index, acc, byte ->
            acc or ((byte.toLong() and BYTE_MASK) shl (BITS_PER_BYTE * index))
        }
    }
}
