package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EpubArchiveTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `reads entries by their exact name only`() {
        val file = zip("OEBPS/" to "", "OEBPS/content.opf" to "<package/>")

        EpubArchive.open(file, ArchiveLimits()).use { archive ->
            assertThat(archive.read("OEBPS/content.opf")?.decodeToString()).isEqualTo("<package/>")
            assertThat(archive.contains("OEBPS/content.opf")).isTrue()
            assertThat(archive.read("oebps/content.opf")).isNull()
            assertThat(archive.read("OEBPS/missing.xhtml")).isNull()
            // ZipFile.getEntry("OEBPS") would return the directory entry "OEBPS/".
            assertThat(archive.read("OEBPS")).isNull()
            assertThat(archive.contains("OEBPS/")).isFalse()
        }
    }

    @Test
    fun `rejects files that are not ZIP archives`() {
        val text = File(directory, "text.epub").apply { writeText("not a zip") }
        val empty = File(directory, "empty.epub").apply { createNewFile() }
        val truncated = File(directory, "truncated.epub")
        truncated.writeBytes(
            zip("mimetype" to "application/epub+zip", "a.xhtml" to "x".repeat(500)).readBytes().copyOf(80),
        )

        for (file in listOf(text, empty, truncated, File(directory, "missing.epub"))) {
            assertThat(openError(file)).isEqualTo(EpubError.NotAZipArchive)
        }
    }

    @Test
    fun `rejects archives with more entries than allowed`() {
        val file = zip("a" to "1", "b" to "2", "c" to "3")

        assertThat(openError(file, ArchiveLimits(maxEntries = 2)))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRIES))
        EpubArchive.open(file, ArchiveLimits(maxEntries = 3)).close()
    }

    @Test
    fun `rejects an entry larger than the per-entry limit`() {
        val file = zip("big.xhtml" to "x".repeat(2_000))

        assertThat(readError(file, "big.xhtml", ArchiveLimits(maxEntryBytes = 1_000)))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
    }

    @Test
    fun `counts decompressed bytes instead of trusting the declared size`() {
        val file = zip("bomb.xhtml" to "\u0000".repeat(1_000_000))
        declareUncompressedSize(file, "bomb.xhtml", 10)

        assertThat(readError(file, "bomb.xhtml", ArchiveLimits(maxEntryBytes = 64 * 1024)))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
    }

    @Test
    fun `stops when the bytes read across entries exceed the total limit`() {
        val file = zip("a.xhtml" to "a".repeat(600), "b.xhtml" to "b".repeat(600))

        EpubArchive.open(file, ArchiveLimits(maxEntryBytes = 1_000, maxTotalBytes = 1_000)).use { archive ->
            assertThat(archive.read("a.xhtml")).hasLength(600)
            val failure = assertThrows<EpubParseException> { archive.read("b.xhtml") }
            assertThat(failure.error).isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_TOTAL_BYTES))
        }
    }

    @Test
    fun `reports a damaged entry as a malformed document`() {
        val file = zip("mimetype" to "application/epub+zip", "OEBPS/c1.xhtml" to "<p>${"texto ".repeat(100)}</p>")
        corruptEntryData(file, "OEBPS/c1.xhtml")

        assertThat(readError(file, "OEBPS/c1.xhtml", ArchiveLimits()))
            .isEqualTo(EpubError.MalformedDocument("OEBPS/c1.xhtml"))
    }

    @Test
    fun `uses hostile entry names only as lookup keys`() {
        val file = zip("../../escape.txt" to "payload", "/absolute.txt" to "payload")
        val before = directory.walk().toSet()

        EpubArchive.open(file, ArchiveLimits()).use { archive ->
            assertThat(archive.read("../../escape.txt")?.decodeToString()).isEqualTo("payload")
            assertThat(archive.read("/absolute.txt")?.decodeToString()).isEqualTo("payload")
        }

        assertThat(directory.walk().toSet()).isEqualTo(before)
        assertThat(File(directory.parentFile.parentFile, "escape.txt").exists()).isFalse()
    }

    private fun zip(vararg entries: Pair<String, String>): File =
        writeZip(
            File(directory, "archive-${entries.size}.zip"),
            entries.map { (name, text) ->
                name to
                    text.toByteArray()
            },
        )

    private fun openError(
        file: File,
        limits: ArchiveLimits = ArchiveLimits(),
    ): EpubError = assertThrows<EpubParseException> { EpubArchive.open(file, limits).close() }.error

    private fun readError(
        file: File,
        path: String,
        limits: ArchiveLimits,
    ): EpubError =
        EpubArchive.open(file, limits).use { archive ->
            assertThrows<EpubParseException> { archive.read(path) }.error
        }
}
