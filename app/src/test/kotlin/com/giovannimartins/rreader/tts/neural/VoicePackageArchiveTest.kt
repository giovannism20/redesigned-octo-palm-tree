package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.tts.neural.TestArchives.FOLDER
import com.giovannimartins.rreader.tts.neural.TestArchives.MODEL
import com.giovannimartins.rreader.tts.neural.TestArchives.TOKENS
import com.giovannimartins.rreader.tts.neural.TestArchives.file
import com.giovannimartins.rreader.tts.neural.TestArchives.link
import com.giovannimartins.rreader.tts.neural.TestArchives.packageEntries
import com.giovannimartins.rreader.tts.neural.TestArchives.tarBz2
import com.giovannimartins.rreader.tts.neural.TestArchives.typed
import com.google.common.truth.Truth.assertThat
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** SR-1 and SR-2 for voice packages: only the expected files are written, under the catalogue's names. */
class VoicePackageArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val target get() = File(folder.root, "out/package")

    @Test
    fun `unpacks only the files the package keeps, under the catalogue's names`() {
        val archive = tarBz2(packageEntries())

        extract(archive)

        assertThat(target.list()?.toList()).containsExactly("model.onnx", "tokens.txt")
        assertThat(File(target, "model.onnx").readBytes()).isEqualTo(MODEL)
        assertThat(File(target, "tokens.txt").readBytes()).isEqualTo(TOKENS)
    }

    @Test
    fun `refuses an archive with an entry that climbs out of its folder or is absolute`() {
        listOf("../../databases/evil", "$FOLDER/../../evil", "/data/evil", "$FOLDER\\..\\evil").forEach { name ->
            val archive = tarBz2(packageEntries() + file(name, "evil".toByteArray()))

            assertThrows(VoiceArchiveException::class.java) { extract(archive) }
        }
        assertThat(
            folder.root
                .walkTopDown()
                .filter { it.name == "evil" }
                .toList(),
        ).isEmpty()
    }

    @Test
    fun `refuses a kept file that is a link, has another size, appears twice or is missing`() {
        val archives =
            listOf(
                listOf(link("$FOLDER/test.onnx", "/data/data/other/model"), file("$FOLDER/tokens.txt", TOKENS)),
                packageEntries(model = MODEL + 1),
                packageEntries() + file("$FOLDER/tokens.txt", TOKENS),
                packageEntries().filterNot { it.name.endsWith("tokens.txt") },
            )

        archives.forEach { entries ->
            assertThrows(VoiceArchiveException::class.java) {
                target.deleteRecursively()
                extract(tarBz2(entries))
            }
        }
    }

    @Test
    fun `refuses a kept file that is a hard link, a device or a named pipe, even of the right size`() {
        val types =
            mapOf(
                "hard link" to TarConstants.LF_LINK,
                "character device" to TarConstants.LF_CHR,
                "block device" to TarConstants.LF_BLK,
                "named pipe" to TarConstants.LF_FIFO,
            )
        types.forEach { (kind, type) ->
            val entries =
                listOf(typed("$FOLDER/test.onnx", type, MODEL, "$FOLDER/other"), file("$FOLDER/tokens.txt", TOKENS))
            target.deleteRecursively()

            assertThrows(kind, VoiceArchiveException::class.java) { extract(tarBz2(entries)) }
        }
        // The same entry as a regular file is unpacked: only its type refuses it.
        target.deleteRecursively()
        extract(
            tarBz2(
                listOf(typed("$FOLDER/test.onnx", TarConstants.LF_NORMAL, MODEL), file("$FOLDER/tokens.txt", TOKENS)),
            ),
        )
        assertThat(File(target, "model.onnx").readBytes()).isEqualTo(MODEL)
    }

    @Test
    fun `skips links, devices and named pipes the package does not keep, creating nothing from them`() {
        val others =
            listOf(
                link("$FOLDER/espeak-ng-data/voices", "/data/data"),
                typed("$FOLDER/espeak-ng-data/hard", TarConstants.LF_LINK, ByteArray(0), "/system/bin/sh"),
                typed("$FOLDER/espeak-ng-data/tty", TarConstants.LF_CHR, ByteArray(0)),
                typed("$FOLDER/espeak-ng-data/pipe", TarConstants.LF_FIFO, ByteArray(0)),
            )

        extract(tarBz2(packageEntries() + others))

        assertThat(target.list()?.toList()).containsExactly("model.onnx", "tokens.txt")
    }

    @Test
    fun `every kept file is flushed to the disk once complete, before the folder can be put in place`() {
        val synced = mutableListOf<Long>()

        extract(tarBz2(packageEntries()), sync = { output: FileOutputStream -> synced += output.channel.size() })

        assertThat(synced).containsExactly(MODEL.size.toLong(), TOKENS.size.toLong()).inOrder()
    }

    @Test
    fun `refuses an archive with too many entries or too much data`() {
        val crowded = tarBz2(packageEntries() + (1..20).map { file("$FOLDER/extra-$it", ByteArray(1)) })
        val bomb = tarBz2(packageEntries() + file("$FOLDER/zeros", ByteArray(2_000_000)))

        assertThrows(VoiceArchiveException::class.java) { extract(crowded, maxEntries = 10) }
        target.deleteRecursively()
        assertThrows(VoiceArchiveException::class.java) { extract(bomb, maxUnpackedBytes = 1_000_000) }
        target.deleteRecursively()
        extract(bomb)
    }

    @Test
    fun `a file that is not a bzip2 tar archive is an error`() {
        assertThrows(IOException::class.java) { extract("<html>not found</html>".toByteArray()) }
    }

    private fun extract(
        archive: ByteArray,
        maxEntries: Int = VoicePackageArchive.MAX_ENTRIES,
        maxUnpackedBytes: Long = VoicePackageArchive.MAX_UNPACKED_BYTES,
        sync: (FileOutputStream) -> Unit = { it.fd.sync() },
    ) {
        val file = File(folder.root, "package.tar.bz2").apply { writeBytes(archive) }
        VoicePackageArchive.extract(file, TestArchives.pkg(archive), target, maxEntries, maxUnpackedBytes, sync)
    }
}
