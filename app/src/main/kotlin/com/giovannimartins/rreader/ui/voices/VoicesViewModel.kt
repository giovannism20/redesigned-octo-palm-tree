package com.giovannimartins.rreader.ui.voices

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.net.Connection
import com.giovannimartins.rreader.net.ConnectionCheck
import com.giovannimartins.rreader.playback.VoiceSelector
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.PhoneEngine
import com.giovannimartins.rreader.tts.RenderSpeedTracker
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import com.giovannimartins.rreader.tts.neural.NeuralVoiceLibrary
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.tts.neural.PackageState
import com.giovannimartins.rreader.tts.neural.VoiceDownloadFailure
import com.giovannimartins.rreader.tts.neural.VoiceDownloadState
import com.giovannimartins.rreader.ui.player.PlaybackControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a neural voice stands, as its card shows it. */
sealed interface NeuralVoiceStatus {
    data object NotDownloaded : NeuralVoiceStatus

    /** Queued: waiting for Wi-Fi ([wifiOnly]) or for any connection. */
    data class Waiting(
        val wifiOnly: Boolean,
    ) : NeuralVoiceStatus

    data class Downloading(
        val fraction: Float,
    ) : NeuralVoiceStatus

    data object Installing : NeuralVoiceStatus

    data class Failed(
        val reason: VoiceDownloadFailure,
    ) : NeuralVoiceStatus

    data object Ready : NeuralVoiceStatus
}

/** One neural voice of the Voices tab. */
data class NeuralVoiceCard(
    val voice: NeuralVoice,
    val status: NeuralVoiceStatus,
    val inUse: Boolean,
    /** The names of the other voices that come in the same download (the Kokoro voices share one). */
    val sharesWith: List<String>,
    /**
     * Whether the voice, or another voice of its package (same model), was measured to render slower than listening
     * at the chosen speed on this phone.
     */
    val slowHere: Boolean = false,
)

/** One of the phone's own voices, in the "Other phone voices" section. */
data class PhoneVoiceCard(
    val label: PhoneVoiceLabel,
    val inUse: Boolean,
)

/** What the Voices tab shows. */
data class VoicesUiState(
    val neural: List<NeuralVoiceCard> = emptyList(),
    /** Whether the "Phone voice" card is the voice in use. */
    val phoneInUse: Boolean = false,
    /** Whether the phone has an offline Portuguese voice of its own; null while its voices are being read. */
    val phoneVoiceAvailable: Boolean? = null,
    val otherPhoneVoices: List<PhoneVoiceCard> = emptyList(),
    /** The voice whose sample plays or is being prepared ([VoiceIds.PHONE] for the phone's voice). */
    val sampleVoiceId: String? = null,
    val sampleFailed: Boolean = false,
    /** The voice waiting for the listener to accept a download over mobile data. */
    val askMobileData: NeuralVoice? = null,
    /** The voice waiting for the listener to confirm its deletion. */
    val askDelete: NeuralVoice? = null,
)

/**
 * State and actions of the Voices tab: the neural voices (download, use, delete, bundled samples), the phone's voice
 * and its other voices. Choosing a voice makes it the default and tells the player, so a book that follows the default
 * voice goes on with it at once ([PlaybackControls.refreshVoice]).
 */
@HiltViewModel
class VoicesViewModel
    @Inject
    constructor(
        private val library: NeuralVoiceLibrary,
        @param:PhoneEngine private val phone: SpeechEngine,
        private val settings: SettingsRepository,
        private val controls: PlaybackControls,
        private val connection: ConnectionCheck,
        private val samples: VoiceSamplePlayer,
        private val audioCache: AudioCache,
        private val renderSpeeds: RenderSpeedTracker,
    ) : ViewModel() {
        private val phoneVoices = MutableStateFlow<List<Voice>?>(null)
        private val ui = MutableStateFlow(UiChoices())

        val state: StateFlow<VoicesUiState> =
            combine(
                library.packages,
                settings.settings,
                phoneVoices,
                ui,
                renderSpeeds.costs,
            ) { packages, prefs, phoneList, choices, costs ->
                val slow = costs.filterValues { it.isKnown && !it.keepsUpAt(prefs.playbackSpeed) }.keys
                VoicesStates.of(packages, prefs.defaultVoiceId ?: VoiceIds.PHONE, phoneList, choices.screen(), slow)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VoicesUiState())

        init {
            refreshPhoneVoices()
        }

        /** Reads the phone's voices again, for example after the listener installed some in the system settings. */
        fun refreshPhoneVoices() {
            viewModelScope.launch {
                phoneVoices.value = phone.voices().filter { it.locale.language == PORTUGUESE }
            }
        }

        /** Makes [voiceId] the default voice; a book that follows the default continues with it at once. */
        fun use(voiceId: String) {
            viewModelScope.launch {
                settings.setDefaultVoice(voiceId)
                controls.refreshVoice()
            }
        }

        /** Downloads [voice]'s package: on Wi-Fi, or offline, it waits for Wi-Fi; on mobile data it asks first. */
        fun download(voice: NeuralVoice) {
            if (connection.current() == Connection.METERED) {
                ui.update { it.copy(askMobileData = voice) }
            } else {
                viewModelScope.launch { library.download(voice.pkg, wifiOnly = true) }
            }
        }

        /** The answer about mobile data: download now ([useMobileData]), wait for Wi-Fi, or neither (null). */
        fun answerMobileData(useMobileData: Boolean?) {
            val voice = ui.value.askMobileData ?: return
            ui.update { it.copy(askMobileData = null) }
            if (useMobileData != null) {
                viewModelScope.launch { library.download(voice.pkg, wifiOnly = !useMobileData) }
            }
        }

        /**
         * Stops the download of [voice], or removes one that failed, and deletes what it received; the voice can be
         * downloaded again.
         */
        fun cancelDownload(voice: NeuralVoice) {
            viewModelScope.launch { library.cancel(voice.pkg) }
        }

        /** Asks the listener to confirm the deletion of [voice]; null closes the question. */
        fun askDelete(voice: NeuralVoice?) = ui.update { it.copy(askDelete = voice) }

        /** Deletes the voice the listener confirmed; listening falls back to another voice at once. */
        fun confirmDelete() {
            val voice = ui.value.askDelete ?: return
            ui.update { it.copy(askDelete = null) }
            viewModelScope.launch {
                stopSample()
                library.delete(voice.pkg)
                controls.refreshVoice()
            }
        }

        /** Plays the sample bundled with [voice], or stops it when it is playing. */
        fun toggleSample(voice: NeuralVoice) {
            if (ui.value.sampleVoiceId == voice.id) {
                stopSample()
            } else {
                play(voice.id, "$SAMPLES${voice.key}.ogg".toUri())
            }
        }

        /**
         * Speaks [sampleText] with a phone voice: [voice], or the phone's own voice when null; stops a sample of the
         * same voice instead.
         */
        fun togglePhoneSample(
            voice: Voice?,
            sampleText: String,
        ) {
            val target = voice ?: phoneVoices.value?.let(VoiceSelector::phoneVoice)
            val id = voice?.id ?: VoiceIds.PHONE
            when {
                ui.value.sampleVoiceId == id -> {
                    stopSample()
                }

                target == null -> {
                    ui.update { it.copy(sampleFailed = true) }
                }

                else -> {
                    stopSample()
                    ui.update { it.copy(sampleVoiceId = id, sampleFailed = false) }
                    viewModelScope.launch {
                        val file = audioCache.sampleFile(target.id)
                        val result = phone.synthesize(sampleText, target, file)
                        if (ui.value.sampleVoiceId != id) return@launch
                        if (result is SynthesisResult.Rendered) {
                            play(id, Uri.fromFile(file))
                        } else {
                            ui.update { it.copy(sampleVoiceId = null, sampleFailed = true) }
                        }
                    }
                }
            }
        }

        /** Stops the sample playing, for example when the screen is left. */
        fun stopSample() {
            samples.stop()
            ui.update { it.copy(sampleVoiceId = null) }
        }

        override fun onCleared() {
            samples.release()
        }

        private fun play(
            voiceId: String,
            uri: Uri,
        ) {
            ui.update { it.copy(sampleVoiceId = voiceId, sampleFailed = false) }
            samples.play(uri) { ui.update { if (it.sampleVoiceId == voiceId) it.copy(sampleVoiceId = null) else it } }
        }

        /** What the listener did on the screen that is not saved anywhere else. */
        private data class UiChoices(
            val sampleVoiceId: String? = null,
            val sampleFailed: Boolean = false,
            val askMobileData: NeuralVoice? = null,
            val askDelete: NeuralVoice? = null,
        ) {
            /** These choices in an otherwise empty screen state. */
            fun screen() =
                VoicesUiState(
                    sampleVoiceId = sampleVoiceId,
                    sampleFailed = sampleFailed,
                    askMobileData = askMobileData,
                    askDelete = askDelete,
                )
        }

        private companion object {
            const val PORTUGUESE = "pt"
            const val SAMPLES = "asset:///voices/samples/"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** Builds the Voices tab's state from the packages, the default voice, the phone's voices and the screen's choices. */
internal object VoicesStates {
    fun of(
        packages: Map<String, PackageState>,
        defaultVoiceId: String,
        phoneList: List<Voice>?,
        choices: VoicesUiState,
        slowVoiceIds: Set<String> = emptySet(),
    ): VoicesUiState {
        val installed = packages.filterValues { it == PackageState.Installed }.keys
        val neuralDefault = NeuralVoices.byId(defaultVoiceId)?.takeIf { it.pkg.id in installed }
        val systemDefault = phoneList?.firstOrNull { it.id == defaultVoiceId }
        // While the phone's voices are read, a default naming one of them cannot be placed yet.
        val placing = phoneList == null && VoiceIds.isSystem(defaultVoiceId)
        return VoicesUiState(
            neural =
                NeuralVoices.all.map { voice ->
                    NeuralVoiceCard(
                        voice = voice,
                        status = statusOf(packages[voice.pkg.id]),
                        inUse = voice == neuralDefault,
                        sharesWith = NeuralVoices.voicesOf(voice.pkg).filter { it != voice }.map(NeuralVoice::name),
                        // The voices of one package share its model, so they render equally fast.
                        slowHere =
                            voice.pkg.id in installed && NeuralVoices.voicesOf(voice.pkg).any { it.id in slowVoiceIds },
                    )
                },
            phoneInUse = neuralDefault == null && systemDefault == null && !placing,
            phoneVoiceAvailable = phoneList?.let { VoiceSelector.phoneVoice(it) != null },
            otherPhoneVoices =
                PhoneVoiceLabels.of(phoneList.orEmpty()).map { PhoneVoiceCard(it, it.voice == systemDefault) },
            sampleVoiceId = choices.sampleVoiceId,
            sampleFailed = choices.sampleFailed,
            askMobileData = choices.askMobileData,
            askDelete = choices.askDelete,
        )
    }

    private fun statusOf(state: PackageState?): NeuralVoiceStatus =
        when (state) {
            PackageState.Installed -> NeuralVoiceStatus.Ready
            is PackageState.Downloading -> statusOf(state.state)
            PackageState.Absent, null -> NeuralVoiceStatus.NotDownloaded
        }

    private fun statusOf(state: VoiceDownloadState): NeuralVoiceStatus =
        when (state) {
            is VoiceDownloadState.Waiting -> NeuralVoiceStatus.Waiting(state.wifiOnly)
            is VoiceDownloadState.Downloading -> NeuralVoiceStatus.Downloading(state.fraction)
            VoiceDownloadState.Installing -> NeuralVoiceStatus.Installing
            is VoiceDownloadState.Failed -> NeuralVoiceStatus.Failed(state.reason)
        }
}
