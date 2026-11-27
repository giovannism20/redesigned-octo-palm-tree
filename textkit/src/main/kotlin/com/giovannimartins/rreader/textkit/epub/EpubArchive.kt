package com.giovannimartins.rreader.textkit.epub

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Read-only, in-memory access to the entries of an EPUB's ZIP archive.
 *
 * Entries are looked up by their exact name and read into memory; names are never turned into file-system paths, so
 * hostile names such as `../../x` cannot touch the disk (SR-1). Every read counts the bytes actually decompressed
 * against the [ArchiveLimits] (SR-2), per entry and for the whole parse.
 */
internal class EpubArchive private constructor(
    private val zip: ZipFile,
    private val limits: ArchiveLimits,
) : Closeable {
    private var totalBytesRead = 0L

    /** Whether the archive has a file entry named exactly [path]. */
    fun contains(path: String): Boolean = fileEntry(path) != null

    /**
     * Reads the file entry named exactly [path], or returns null when there is none. A damaged entry aborts the parse
     * with [EpubError.MalformedDocument]; exceeding a limit aborts it with [EpubError.LimitExceeded].
     */
    fun read(path: String): ByteArray? {
        val entry = fileEntry(path) ?: return null
        if (entry.size > limits.maxEntryBytes) abortParse(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
        return try {
            zip.getInputStream(entry).use(::readWithinLimits)
        } catch (e: IOException) {
            abortParse(EpubError.MalformedDocument(path), e)
        }
    }

    override fun close() {
        zip.close()
    }

    // ZipFile.getEntry("x") also finds a directory entry "x/", so the name and kind are checked again.
    private fun fileEntry(path: String): ZipEntry? = zip.getEntry(path)?.takeIf { it.name == path && !it.isDirectory }

    private fun readWithinLimits(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var entryBytes = 0L
        var count = input.read(buffer)
        while (count >= 0) {
            entryBytes += count
            totalBytesRead += count
            enforceByteLimits(entryBytes)
            output.write(buffer, 0, count)
            count = input.read(buffer)
        }
        return output.toByteArray()
    }

    private fun enforceByteLimits(entryBytes: Long) {
        val exceeded =
            when {
                entryBytes > limits.maxEntryBytes -> ArchiveLimit.MAX_ENTRY_BYTES
                totalBytesRead > limits.maxTotalBytes -> ArchiveLimit.MAX_TOTAL_BYTES
                else -> null
            }
        if (exceeded != null) abortParse(EpubError.LimitExceeded(exceeded))
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024

        /**
         * Opens [file] as a ZIP archive and checks its entry count. Aborts the parse with [EpubError.NotAZipArchive]
         * when the file cannot be opened as one.
         */
        fun open(
            file: File,
            limits: ArchiveLimits,
        ): EpubArchive {
            val zip =
                try {
                    ZipFile(file)
                } catch (e: IOException) {
                    abortParse(EpubError.NotAZipArchive, e)
                }
            if (zip.size() > limits.maxEntries) {
                zip.close()
                abortParse(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRIES))
            }
            return EpubArchive(zip, limits)
        }
    }
}
