package com.giovannimartins.rreader.tts.neural

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Why a package archive was refused. */
class VoiceArchiveException(
    message: String,
) : IOException(message)

/**
 * Unpacks the files a voice keeps from its package archive, a bzip2-compressed tar, after the archive passed its
 * SHA-256 check. Defence in depth for SR-1 and SR-2: entry names are only compared with the names the catalogue
 * expects and never used as paths (each kept file is written under its [PackageFile.name]); an archive with an
 * absolute or `..` entry, more than [MAX_ENTRIES] entries or more than [MAX_UNPACKED_BYTES] in all is refused; a
 * kept file must be a regular file of exactly its expected size and appear once; and every kept file must be found.
 */
internal object VoicePackageArchive {
    /** More entries than any official package has (the Kokoro one has about 420). */
    const val MAX_ENTRIES: Int = 2_000

    /** More than any official package unpacks to (the Kokoro one, about 200 MB). */
    const val MAX_UNPACKED_BYTES: Long = 512L * 1024 * 1024

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Writes the [pkg] files found in [archive] into [target], which is created and must be empty. The limits are
     * parameters for tests.
     *
     * @throws VoiceArchiveException when the archive is not what the catalogue describes.
     * @throws IOException when it cannot be read or the files cannot be written.
     */
    fun extract(
        archive: File,
        pkg: VoicePackage,
        target: File,
        maxEntries: Int = MAX_ENTRIES,
        maxUnpackedBytes: Long = MAX_UNPACKED_BYTES,
    ) {
        if (!target.mkdirs() && !target.isDirectory) throw IOException("Cannot create the package folder")
        val wanted = pkg.files.associateBy(PackageFile::entry)
        val found = mutableSetOf<String>()
        BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()), true).use { bzip2 ->
            TarArchiveInputStream(Limited(bzip2, maxUnpackedBytes)).use { tar ->
                forEachEntry(tar, maxEntries) { entry ->
                    wanted[entry.name]?.let { file ->
                        refuse(!found.add(file.entry), "A file appears twice")
                        copy(tar, entry, file, File(target, file.name))
                    }
                }
            }
        }
        refuse(found.size != wanted.size, "Files are missing")
    }

    /** Calls [action] with every entry of [tar] whose name is safe; more than [maxEntries] entries refuse it. */
    private fun forEachEntry(
        tar: TarArchiveInputStream,
        maxEntries: Int,
        action: (TarArchiveEntry) -> Unit,
    ) {
        var entries = 0
        var entry = nextEntry(tar)
        while (entry != null) {
            entries++
            refuse(entries > maxEntries, "Too many entries")
            check(entry.name)
            action(entry)
            entry = nextEntry(tar)
        }
    }

    /** Refuses the archive with [reason] when [condition] holds. */
    private fun refuse(
        condition: Boolean,
        reason: String,
    ) {
        if (condition) throw VoiceArchiveException(reason)
    }

    /**
     * The next entry, read through the base type: its signature is the same in every Commons Compress version, and
     * the Android jar of Robolectric carries an older copy of the library that unit tests load instead of ours.
     */
    private fun nextEntry(tar: ArchiveInputStream<*>): TarArchiveEntry? = tar.nextEntry as TarArchiveEntry?

    /** Refuses names that would point outside a folder if anything used them as paths. */
    private fun check(name: String) {
        val unsafe =
            name.isEmpty() ||
                name.startsWith("/") ||
                name.contains('\\') ||
                name.contains(Char(0)) ||
                name.split('/').any { it == ".." }
        refuse(unsafe, "Unsafe entry name")
    }

    private fun copy(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        file: PackageFile,
        destination: File,
    ) {
        val regular = entry.isFile && !entry.isSymbolicLink && !entry.isLink
        refuse(!regular || entry.size != file.bytes, "A file is not what was expected")
        var copied = 0L
        destination.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read = tar.read(buffer)
            while (read > 0) {
                output.write(buffer, 0, read)
                copied += read
                read = tar.read(buffer)
            }
        }
        refuse(copied != file.bytes, "A file is incomplete")
    }

    /** Fails once more than [limit] bytes were read: a bound on what a hostile archive can unpack to. */
    private class Limited(
        input: InputStream,
        private val limit: Long,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) counted(1) }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = super.read(b, off, len).also { if (it > 0) counted(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) counted(it) }

        private fun counted(bytes: Long) {
            count += bytes
            refuse(count > limit, "The archive unpacks to too much data")
        }
    }
}
