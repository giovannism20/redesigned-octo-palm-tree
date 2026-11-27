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
 * progress is `voices/.downloads/{id}.part` and an installation being unpacked `voices/.staging/{id}/`, so a package
 * folder only ever appears complete. A package is installed when every kept file is there with its exact size.
 */
@Singleton
class VoicePackageStore internal constructor(
    private val root: File,
    private val io: CoroutineDispatcher,
    /** Bytes free for the app in the storage that holds a folder; replaced in tests. */
    private val spaceOf: (File) -> Long = File::getUsableSpace,
    /** The packages that can be installed. */
    private val packages: List<VoicePackage> = NeuralVoices.packages,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(File(context.filesDir, "voices"), io)

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

    /** The archive of [pkg] being downloaded; kept between attempts so a download resumes where it stopped. */
    fun partialFile(pkg: VoicePackage): File = File(File(root, DOWNLOADS), "${pkg.id}.part")

    /** The folder [pkg] is unpacked into before it becomes its [directory]. */
    fun stagingDirectory(pkg: VoicePackage): File = File(File(root, STAGING), pkg.id)

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

    /** Removes the partial download and the unfinished installation of [pkg], keeping an installed copy. Blocking. */
    fun discardDownload(pkg: VoicePackage) {
        partialFile(pkg).delete()
        stagingDirectory(pkg).deleteRecursively()
    }

    private companion object {
        const val DOWNLOADS = ".downloads"
        const val STAGING = ".staging"
    }
}
