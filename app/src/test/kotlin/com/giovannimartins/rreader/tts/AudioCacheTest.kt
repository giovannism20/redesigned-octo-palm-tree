package com.giovannimartins.rreader.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bookId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun `segment files are separated by book and voice`() {
        val cache = AudioCache(folder.root)

        val a = cache.segmentFile(bookId, "system:engine:voice-a", 0, 0, 10)
        val b = cache.segmentFile(bookId, "system:engine:voice-b", 0, 0, 10)

        assertThat(a).isNotEqualTo(b)
        assertThat(a.name).isEqualTo("0-0-10.wav")
        assertThat(a.path).contains(bookId)
    }

    @Test
    fun `trim removes the oldest files first and keeps protected ones`() {
        val cache = AudioCache(folder.root)
        val files = (0 until 4).map { index -> cache.segmentFile(bookId, "v", 0, index * 10, index * 10 + 10) }
        files.forEachIndexed { index, file ->
            file.parentFile!!.mkdirs()
            file.writeBytes(ByteArray(100))
            file.setLastModified(1_000L * (index + 1))
        }

        cache.trim(maxBytes = 200, keep = setOf(files[0]))

        assertThat(files.map { it.exists() }).containsExactly(true, false, false, true).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects ids that could escape the cache directory`() {
        AudioCache(folder.root).segmentFile("../../etc", "v", 0, 0, 1)
    }
}
