package com.giovannimartins.rreader.tts.neural

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Builds bzip2-compressed tar archives like the voice packages, including hostile ones, for tests. */
internal object TestArchives {
    sealed interface Entry {
        val name: String
    }

    class FileEntry(
        override val name: String,
        val bytes: ByteArray,
    ) : Entry

    class LinkEntry(
        override val name: String,
        val target: String,
    ) : Entry

    fun file(
        name: String,
        bytes: ByteArray,
    ): Entry = FileEntry(name, bytes)

    fun link(
        name: String,
        target: String,
    ): Entry = LinkEntry(name, target)

    fun tarBz2(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        BZip2CompressorOutputStream(out).use { bzip2 ->
            TarArchiveOutputStream(bzip2).use { tarStream ->
                tarStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                // Written through the base type, whose signatures are the same in every Commons Compress version:
                // under Robolectric, its Android jar's older copy of the library is the one loaded.
                @Suppress("UNCHECKED_CAST")
                val tar = tarStream as ArchiveOutputStream<ArchiveEntry>
                entries.forEach { entry -> write(tar, entry) }
            }
        }
        return out.toByteArray()
    }

    private fun write(
        tar: ArchiveOutputStream<ArchiveEntry>,
        entry: Entry,
    ) {
        when (entry) {
            is FileEntry -> {
                tar.putArchiveEntry(TarArchiveEntry(entry.name, true).apply { size = entry.bytes.size.toLong() })
                tar.write(entry.bytes)
            }

            is LinkEntry -> {
                tar.putArchiveEntry(
                    TarArchiveEntry(entry.name, TarConstants.LF_SYMLINK, true).apply { linkName = entry.target },
                )
            }
        }
        tar.closeArchiveEntry()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** A small package like the Piper ones: a model and its tokens in a folder of [archive] (built from [entries]). */
    fun pkg(
        archive: ByteArray,
        model: ByteArray = MODEL,
        tokens: ByteArray = TOKENS,
        sha256: String = sha256(archive),
    ) = VoicePackage(
        id = "test",
        family = ModelFamily.PIPER,
        archive = "vits-piper-test.tar.bz2",
        archiveBytes = archive.size.toLong(),
        sha256 = sha256,
        files =
            listOf(
                PackageFile("$FOLDER/test.onnx", "model.onnx", model.size.toLong()),
                PackageFile("$FOLDER/tokens.txt", "tokens.txt", tokens.size.toLong()),
            ),
    )

    /** The entries of an official-looking package: the kept files plus others that are skipped. */
    fun packageEntries(
        model: ByteArray = MODEL,
        tokens: ByteArray = TOKENS,
    ): List<Entry> =
        listOf(
            file("$FOLDER/MODEL_CARD", "card".toByteArray()),
            file("$FOLDER/test.onnx", model),
            file("$FOLDER/tokens.txt", tokens),
            file("$FOLDER/espeak-ng-data/pt_dict", ByteArray(64) { 7 }),
        )

    const val FOLDER = "vits-piper-test"
    val MODEL = ByteArray(40_000) { (it * 31).toByte() }
    val TOKENS = "_ 0\n^ 1\n$ 2\n".toByteArray()
}
