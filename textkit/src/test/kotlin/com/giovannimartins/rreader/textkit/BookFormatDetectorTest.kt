package com.giovannimartins.rreader.textkit

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookFormatDetectorTest {
    @Test
    fun `detects a PDF from its header`() {
        val pdf = "%PDF-1.7\n%âãÏÓ\n1 0 obj".toByteArray(Charsets.ISO_8859_1)

        assertThat(BookFormatDetector.detect(pdf)).isEqualTo(FormatDetection.Detected(BookFormat.PDF))
    }

    @Test
    fun `detects a PDF whose marker follows a few bytes of junk`() {
        val pdf = ByteArray(16) { 0x20 } + "%PDF-1.4".toByteArray(Charsets.US_ASCII)

        assertThat(BookFormatDetector.detect(pdf)).isEqualTo(FormatDetection.Detected(BookFormat.PDF))
    }

    @Test
    fun `detects a conforming EPUB from its stored mimetype entry`() {
        val epub = zipOf("mimetype" to "application/epub+zip", "META-INF/container.xml" to "<container/>")

        assertThat(BookFormatDetector.detect(epub)).isEqualTo(FormatDetection.Detected(BookFormat.EPUB))
    }

    @Test
    fun `reports a ZIP without the mimetype marker as a plain archive`() {
        val zip = zipOf("META-INF/container.xml" to "<container/>", "mimetype" to "application/epub+zip")

        assertThat(BookFormatDetector.detect(zip)).isEqualTo(FormatDetection.ZipArchive)
    }

    @Test
    fun `reports a ZIP whose mimetype is not EPUB as a plain archive`() {
        val zip = zipOf("mimetype" to "application/zip")

        assertThat(BookFormatDetector.detect(zip)).isEqualTo(FormatDetection.ZipArchive)
    }

    @Test
    fun `rejects unknown, empty and truncated content without throwing`() {
        assertThat(BookFormatDetector.detect(ByteArray(0))).isEqualTo(FormatDetection.Unsupported)
        assertThat(BookFormatDetector.detect("hello".toByteArray())).isEqualTo(FormatDetection.Unsupported)
        assertThat(BookFormatDetector.detect(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
            .isEqualTo(FormatDetection.ZipArchive)
    }

    @Test
    fun `ignores markers beyond the header window`() {
        val late = ByteArray(BookFormatDetector.HEADER_SIZE) { 0x20 } + "%PDF-1.7".toByteArray(Charsets.US_ASCII)

        assertThat(BookFormatDetector.detect(late)).isEqualTo(FormatDetection.Unsupported)
    }

    /** Builds a ZIP whose first entry is STORED, like the EPUB OCF specification requires for `mimetype`. */
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEachIndexed { index, (name, content) ->
                val data = content.toByteArray(Charsets.US_ASCII)
                val entry = ZipEntry(name)
                if (index == 0) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
