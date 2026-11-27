package com.giovannimartins.rreader.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes mono 16-bit PCM WAV files, the format the player and [WavInfo] read. */
internal object PcmWav {
    private const val HEADER_BYTES = 44
    private const val FMT_CHUNK_BYTES = 16
    private const val PCM_FORMAT: Short = 1
    private const val CHANNELS: Short = 1
    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_SAMPLE: Short = 16
    private const val RIFF_SIZE_WITHOUT_DATA = 36

    /** Writes [samples] at [sampleRate] to [file], replacing it. */
    fun write(
        file: File,
        samples: ShortArray,
        sampleRate: Int,
    ) {
        val dataBytes = samples.size * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(RIFF_SIZE_WITHOUT_DATA + dataBytes)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(FMT_CHUNK_BYTES)
        buffer.putShort(PCM_FORMAT)
        buffer.putShort(CHANNELS)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * BYTES_PER_SAMPLE)
        buffer.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        buffer.putShort(BITS_PER_SAMPLE)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        samples.forEach(buffer::putShort)
        file.writeBytes(buffer.array())
    }
}
