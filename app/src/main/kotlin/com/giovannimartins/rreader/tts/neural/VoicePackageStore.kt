package com.giovannimartins.rreader.tts.neural

import android.content.Context
import com.giovannimartins.rreader.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where voice packages live on the phone: `filesDir/voices/{package id}/` holds the files a package keeps, under the
 * names the catalogue gives them ([PackageFile.name]), never names read from an archive (SR-1). A download in
 * progress is `voices/.downloads/{id}-{pin}.part`, `{pin}` being the first [PIN_DIGITS] hex digits of the archive's
 * pinned SHA-256, so the bytes of an archive pinned before are never resumed into another; next to it,
 * `{id}-{pin}.stalls` counts the attempts in a row that received nothing new. An installation being unpacked is
 * `voices/.staging/{id}/`, so a package folder only ever appears complete. A package is installed when every kept file
 * is there with its exact size.
 */
@Singleton
class VoicePackageStore internal constructor(
    private val root: File,
    private val io: CoroutineDispatcher,
    /** Bytes free for the app in the storage that holds a folder; replaced in tests. */
    private val spaceOf: (File) -> Long = File::getUsableSpace,
    /** The packages that can be installed. */
    val packages: List<VoicePackage> = NeuralVoices.packages,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(File(context.filesDir, FOLDER), io)

    private val downloadsFolder = File(root, DOWNLOADS)
    private val stagingFolder = File(root, STAGING)
    private val installedIds = MutableStateFlow<Set<String>?>(null)

    /** The ids of the installed packages, read from storage the first time and updated as packages come and go. */
    val installed: Flow<Set<String>> =
        flow {
            installedNow()
            emitAll(installedIds.filterNotNull())
        }

    /** The ids of the installed packages now. */
    suspend fun installedNow(): Set<String> = installedIds.value ?: rescan()

    /** Reads again which packages are installed. */
    suspend fun rescan(): Set<String> =
        withContext(io) {
            packages
                .filter(::isComplete)
                .map(VoicePackage::id)
                .toSet()
                .also { installedIds.value = it }
        }

    /** The folder of [pkg]'s files. */
    fun directory(pkg: VoicePackage): File = File(root, pkg.id)

    /** The installed file of [pkg] called [name] (one of its [PackageFile.name]s). */
    fun file(
        pkg: VoicePackage,
        name: String,
    ): File = File(directory(pkg), name)

    /**
     * The archive of [pkg] being downloaded, kept between attempts so a download resumes where it stopped. Its name
     * carries the archive's pin, so a download whose pin changed starts over.
     */
    fun partialFile(pkg: VoicePackage): File = File(downloadsFolder, "${downloadName(pkg)}.$PART")

    /** The folder [pkg] is unpacked into before it becomes its [directory]. */
    fun stagingDirectory(pkg: VoicePackage): File = File(stagingFolder, pkg.id)

    /** How many attempts in a row received nothing new of [pkg]'s archive. Blocking. */
    fun stalledAttempts(pkg: VoicePackage): Int =
        stallsFile(pkg)
            .takeIf(File::isFile)
            ?.readText()
            ?.trim()
            ?.toIntOrNull()
            ?.coerceAtLeast(0) ?: 0

    /** Remembers that [count] attempts in a row received nothing new of [pkg]'s archive; 0 forgets them. Blocking. */
    fun recordStalledAttempts(
        pkg: VoicePackage,
        count: Int,
    ) {
        val file = stallsFile(pkg)
        if (count > 0) {
            file.parentFile?.mkdirs()
            file.writeText(count.toString())
        } else {
            file.delete()
        }
    }

    /** Whether every file [pkg] keeps is installed with its exact size. Blocking. */
    fun isComplete(pkg: VoicePackage): Boolean = pkg.files.all { file(pkg, it.name).length() == it.bytes }

    /** Bytes free for downloads in the app's storage. Blocking. */
    fun usableBytes(): Long = spaceOf(root.apply { mkdirs() })

    /** Makes the unpacked [staging] folder the installed [pkg], replacing any earlier copy. */
    suspend fun install(
        pkg: VoicePackage,
        staging: File,
    ) {
        withContext(io) {
            val target = directory(pkg)
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("The voice package could not be put in place")
        }
        rescan()
    }

    /** Removes [pkg]: its files, a partial download and an unfinished installation. */
    suspend fun delete(pkg: VoicePackage) {
        withContext(io) {
            directory(pkg).deleteRecursively()
            discardDownload(pkg)
        }
        rescan()
    }

    /**
     * Removes what downloads of [pkg] left, its partial archive for any pin, its count of stalled attempts and its
     * unfinished installation, keeping an installed copy. Blocking.
     */
    fun discardDownload(pkg: VoicePackage) {
        downloadFilesOf(pkg).forEach(File::delete)
        stagingDirectory(pkg).deleteRecursively()
    }

    /** Removes the partial archives of [pkg] for pins other than its own, which are never resumed. Blocking. */
    fun discardStaleDownloads(pkg: VoicePackage) {
        val current = currentNames(pkg)
        downloadFilesOf(pkg).filterNot { it.name in current }.forEach(File::delete)
    }

    /**
     * Removes from the download and staging folders what belongs to no package's current download: archives of pins
     * changed since, of packages no longer offered or of earlier versions of the app, and anything else. The current
     * download of each package is left to [discardDownload]. Blocking.
     */
    fun discardStrays() {
        val current = packages.flatMap(::currentNames).toSet()
        downloadsFolder
            .listFiles()
            .orEmpty()
            .filterNot { it.name in current }
            .forEach(File::deleteRecursively)
        val ids = packages.map(VoicePackage::id).toSet()
        stagingFolder
            .listFiles()
            .orEmpty()
            .filterNot { it.name in ids }
            .forEach(File::deleteRecursively)
    }

    /** `{id}-{pin}`: the name of [pkg]'s download files, without their extension. */
    private fun downloadName(pkg: VoicePackage) = "${pkg.id}-${pkg.sha256.take(PIN_DIGITS)}"

    private fun stallsFile(pkg: VoicePackage): File = File(downloadsFolder, "${downloadName(pkg)}.$STALLS")

    /** The names of the files a download of [pkg] keeps now. */
    private fun currentNames(pkg: VoicePackage): Set<String> = setOf(partialFile(pkg).name, stallsFile(pkg).name)

    /** The download files of [pkg] for any pin, and `{id}.part`, the name earlier versions of the app used. */
    private fun downloadFilesOf(pkg: VoicePackage): List<File> {
        val names = Regex(Regex.escape(pkg.id) + "(-[0-9a-f]{$PIN_DIGITS})?\\.($PART|$STALLS)")
        return downloadsFolder.listFiles().orEmpty().filter { names.matches(it.name) }
    }

    companion object {
        /**
         * The folder of the voice packages in the app's files, `filesDir/voices`. Device transfer leaves it out
         * (`data_extraction_rules.xml`): packages are downloaded again, and checked against their pins, on a new phone.
         */
        const val FOLDER = "voices"

        /** Hex digits of the pinned SHA-256 in the name of a partial archive. */
        const val PIN_DIGITS = 16

        private const val DOWNLOADS = ".downloads"
        private const val STAGING = ".staging"
        private const val PART = "part"
        private const val STALLS = "stalls"
    }
}
