package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.tts.VoiceIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Where a voice package stands for the listener. */
sealed interface PackageState {
    /** Not on the phone, and no download asked for (or the last one was cancelled). */
    data object Absent : PackageState

    data class Downloading(
        val state: VoiceDownloadState,
    ) : PackageState

    data object Installed : PackageState
}

/**
 * The neural voices as the listener manages them: which packages are installed or being downloaded, and downloading,
 * cancelling and deleting them. Deleting a package frees its model first, and a default voice it provided gives way
 * to the phone's voice (books that named it fall back the same way, see `VoiceSelector`).
 */
@Singleton
class NeuralVoiceLibrary
    @Inject
    constructor(
        private val store: VoicePackageStore,
        private val downloads: VoiceDownloads,
        private val engine: SherpaOnnxEngine,
        private val settings: SettingsRepository,
    ) {
        /** Every package's state, by package id. */
        val packages: Flow<Map<String, PackageState>> =
            combine(store.installed, downloads.states) { installed, jobs ->
                NeuralVoices.packages.associate { pkg ->
                    val job = jobs[pkg.id]
                    pkg.id to
                        when {
                            pkg.id in installed -> PackageState.Installed
                            job != null -> PackageState.Downloading(job)
                            else -> PackageState.Absent
                        }
                }
            }

        suspend fun download(
            pkg: VoicePackage,
            wifiOnly: Boolean,
        ) = downloads.download(pkg, wifiOnly)

        suspend fun cancel(pkg: VoicePackage) = downloads.cancel(pkg)

        /** Deletes [pkg] from the phone. */
        suspend fun delete(pkg: VoicePackage) {
            val default = settings.settings.first().defaultVoiceId
            if (NeuralVoices.byId(default)?.pkg == pkg) settings.setDefaultVoice(VoiceIds.PHONE)
            engine.unload(pkg)
            store.delete(pkg)
        }
    }
