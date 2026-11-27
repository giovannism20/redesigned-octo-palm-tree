package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.net.FetchFailure
import com.giovannimartins.rreader.net.FetchResult
import com.giovannimartins.rreader.net.HttpsRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Why a voice package was not installed, as the listener is told. */
enum class VoiceDownloadFailure {
    /** The internet connection was missing or broke, also on the last attempt. */
    NETWORK,

    /** The package could not be downloaded: the server was busy, refused, or answered with something else. */
    UNAVAILABLE,

    /** The downloaded file was not the published package (wrong size or SHA-256, or an archive that is refused). */
    DAMAGED,

    /** There is not enough free space on the phone. */
    STORAGE,
}

/** How far a package download got. */
sealed interface VoiceDownloadProgress {
    /** [fraction] of the archive received, between 0 and 1. */
    data class Downloading(
        val fraction: Float,
    ) : VoiceDownloadProgress

    /** The archive is checked and unpacked. */
    data object Installing : VoiceDownloadProgress
}

/** Outcome of [VoicePackageDownloader.download]. */
sealed interface VoiceDownloadOutcome {
    data object Installed : VoiceDownloadOutcome

    /**
     * Not installed; a [retryable] failure may work on a later attempt, which resumes the download. A failure that may
     * pass is no longer retryable once [VoicePackageDownloader.MAX_STALLED_ATTEMPTS] attempts in a row received nothing
     * new of the archive.
     */
    data class Failed(
        val reason: VoiceDownloadFailure,
        val retryable: Boolean,
    ) : VoiceDownloadOutcome
}

/**
 * Downloads, verifies and installs a voice package (ADR 0018, SR-6):
 *
 * 1. Checks there is room for the rest of the archive plus the unpacked files.
 * 2. Fetches the archive over [VoiceModelHttp] (allowlisted HTTPS, at most the pinned size) into
 *    [VoicePackageStore.partialFile], continuing a partial file left by an earlier attempt for the same pin; when the
 *    server will not continue it, starts again once from the beginning.
 * 3. Checks the archive's size and SHA-256 against the values pinned in [NeuralVoices]; a mismatch deletes it.
 * 4. Unpacks the kept files into a staging folder ([VoicePackageArchive]) and moves it into place, then deletes the
 *    archive.
 *
 * Network failures keep the partial file so the next attempt resumes, until [MAX_STALLED_ATTEMPTS] attempts in a row
 * have received nothing new of it; an attempt that receives part of the archive starts the count again, so a long
 * download that keeps being interrupted is never given up while it advances. A cancelled download keeps the partial
 * file too; [discard] removes it. A package found installed has what its downloads left deleted, which a process that
 * died just after putting it in place leaves. One download at a time per package: a second request waits for the
 * first to finish or stop.
 */
@Singleton
class VoicePackageDownloader
    @Inject
    constructor(
        private val http: VoiceModelHttp,
        private val store: VoicePackageStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val locks = ConcurrentHashMap<String, Mutex>()

        /**
         * Installs [pkg], telling [onProgress] how it goes. [firstAttempt] is false for the later attempts of the same
         * download, which go on counting the attempts in a row that received nothing new.
         */
        suspend fun download(
            pkg: VoicePackage,
            firstAttempt: Boolean = true,
            onProgress: (VoiceDownloadProgress) -> Unit = {},
        ): VoiceDownloadOutcome =
            lockOf(pkg).withLock {
                withContext(io) {
                    try {
                        if (firstAttempt) store.recordStalledAttempts(pkg, 0)
                        if (store.isComplete(pkg)) {
                            store.discardDownload(pkg)
                            store.rescan()
                            VoiceDownloadOutcome.Installed
                        } else {
                            fetchAndInstall(pkg, onProgress)
                        }
                    } catch (e: VoiceArchiveException) {
                        Diagnostics.recordHandled("unpack voice package", e)
                        store.discardDownload(pkg)
                        DAMAGED
                    } catch (e: IOException) {
                        // The archive or the files could not be written: the phone's storage.
                        Diagnostics.recordHandled("save voice package", e)
                        store.stagingDirectory(pkg).deleteRecursively()
                        VoiceDownloadOutcome.Failed(VoiceDownloadFailure.STORAGE, retryable = false)
                    }
                }
            }

        /** Deletes what a stopped download of [pkg] left, after any download of it running now has stopped. */
        suspend fun discard(pkg: VoicePackage) {
            lockOf(pkg).withLock { withContext(io) { store.discardDownload(pkg) } }
        }

        /**
         * Deletes what downloads left behind, except the current download of the packages in [activeIds], whose jobs
         * wait or run: what every other package's downloads left (each once a download of it running now has
         * stopped), and whatever belongs to no package's current download.
         */
        suspend fun discardLeftovers(activeIds: Set<String>) {
            store.packages.filterNot { it.id in activeIds }.forEach { discard(it) }
            withContext(io) { store.discardStrays() }
        }

        private suspend fun fetchAndInstall(
            pkg: VoicePackage,
            onProgress: (VoiceDownloadProgress) -> Unit,
        ): VoiceDownloadOutcome {
            store.discardStaleDownloads(pkg)
            val partial = store.partialFile(pkg)
            if (partial.length() > pkg.archiveBytes) partial.delete()
            val before = partial.length()
            val missing = pkg.archiveBytes - before
            if (store.usableBytes() < missing + pkg.installedBytes + SPACE_MARGIN) {
                return VoiceDownloadOutcome.Failed(VoiceDownloadFailure.STORAGE, retryable = false)
            }
            val fetched = if (missing > 0) fetch(pkg, partial, onProgress) else FetchResult.Received(pkg.archiveBytes)
            return when (fetched) {
                is FetchResult.Failed -> failed(pkg, fetched.failure, advanced = partial.length() > before)
                is FetchResult.Received -> install(pkg, partial, onProgress)
            }
        }

        /** Fetches the rest of the archive into [partial], or the whole of it when the server will not resume. */
        private suspend fun fetch(
            pkg: VoicePackage,
            partial: File,
            onProgress: (VoiceDownloadProgress) -> Unit,
        ): FetchResult {
            val first = fetchFrom(pkg, partial, partial.length(), onProgress)
            if (first !is FetchResult.Failed || first.failure != FetchFailure.RESUME_REFUSED) return first
            partial.delete()
            return fetchFrom(pkg, partial, 0, onProgress)
        }

        private suspend fun fetchFrom(
            pkg: VoicePackage,
            partial: File,
            offset: Long,
            onProgress: (VoiceDownloadProgress) -> Unit,
        ): FetchResult {
            partial.parentFile?.mkdirs()
            val request = HttpsRequest(pkg.url, ARCHIVE_TYPES, pkg.archiveBytes, resumeFrom = offset)
            return FileOutputStream(partial, offset > 0).use { sink ->
                http.fetch(request, sink) { received, _ ->
                    // Whole percentages, so the job's progress changes about a hundred times.
                    val percent = (received * PERCENT / pkg.archiveBytes).toInt()
                    onProgress(VoiceDownloadProgress.Downloading(percent / PERCENT.toFloat()))
                }
            }
        }

        private suspend fun install(
            pkg: VoicePackage,
            partial: File,
            onProgress: (VoiceDownloadProgress) -> Unit,
        ): VoiceDownloadOutcome {
            onProgress(VoiceDownloadProgress.Installing)
            if (partial.length() != pkg.archiveBytes || sha256(partial) != pkg.sha256) {
                store.discardDownload(pkg)
                return DAMAGED
            }
            val staging = store.stagingDirectory(pkg)
            staging.deleteRecursively()
            VoicePackageArchive.extract(partial, pkg, staging)
            currentCoroutineContext().ensureActive()
            store.install(pkg, staging)
            store.discardDownload(pkg)
            return VoiceDownloadOutcome.Installed
        }

        /** The outcome of a fetch that ended with [failure], after it [advanced] the partial archive or not. */
        private fun failed(
            pkg: VoicePackage,
            failure: FetchFailure,
            advanced: Boolean,
        ): VoiceDownloadOutcome.Failed {
            val reason =
                if (failure == FetchFailure.NETWORK) VoiceDownloadFailure.NETWORK else VoiceDownloadFailure.UNAVAILABLE
            if (!failure.retryable) {
                // Failures that will not pass leave nothing behind.
                store.discardDownload(pkg)
                return VoiceDownloadOutcome.Failed(reason, retryable = false)
            }
            // The partial file is kept for the next attempt to resume, while attempts keep receiving something.
            val stalled = if (advanced) 0 else store.stalledAttempts(pkg) + 1
            store.recordStalledAttempts(pkg, stalled)
            return VoiceDownloadOutcome.Failed(reason, retryable = stalled < MAX_STALLED_ATTEMPTS)
        }

        private suspend fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    digest.update(buffer, 0, read)
                    currentCoroutineContext().ensureActive()
                    read = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun lockOf(pkg: VoicePackage): Mutex = locks.getOrPut(pkg.id) { Mutex() }

        companion object {
            /** Free space kept beyond what a package needs, so the phone is not filled to the last byte. */
            const val SPACE_MARGIN: Long = 50L * 1024 * 1024

            /**
             * Attempts in a row that may end without receiving anything new of an archive before its download gives
             * up. Runs the system stops (the Wi-Fi was lost, background work's time limit) are not attempts that ended.
             */
            const val MAX_STALLED_ATTEMPTS: Int = 6

            private const val BUFFER_SIZE = 64 * 1024
            private const val PERCENT = 100L

            /** GitHub serves release assets as binary data. */
            private val ARCHIVE_TYPES = setOf("application/octet-stream", "application/x-bzip2", "application/x-bzip")
            private val DAMAGED = VoiceDownloadOutcome.Failed(VoiceDownloadFailure.DAMAGED, retryable = false)
        }
    }
