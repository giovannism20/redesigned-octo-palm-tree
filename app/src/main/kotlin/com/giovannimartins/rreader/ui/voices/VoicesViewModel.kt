package com.giovannimartins.rreader.ui.voices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.SystemTtsEngine
import com.giovannimartins.rreader.tts.Voice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Voices screen state. */
data class VoicesUiState(
    val loading: Boolean = true,
    /** Portuguese voices installed on the phone, offline ones first. */
    val voices: List<Voice> = emptyList(),
    val selectedVoiceId: String? = null,
    val preparingSampleId: String? = null,
    val sampleFailed: Boolean = false,
)

@HiltViewModel
class VoicesViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: SystemTtsEngine,
        private val settings: SettingsRepository,
        private val audioCache: AudioCache,
    ) : ViewModel() {
        private val speechEngine: SpeechEngine = engine
        private val _state = MutableStateFlow(VoicesUiState())
        val state: StateFlow<VoicesUiState> = _state.asStateFlow()
        private var samplePlayer: ExoPlayer? = null

        init {
            viewModelScope.launch {
                val voices = speechEngine.voices().filter { it.locale.language == PORTUGUESE }
                _state.update { it.copy(loading = false, voices = voices) }
            }
            viewModelScope.launch {
                settings.settings.collect { prefs -> _state.update { it.copy(selectedVoiceId = prefs.defaultVoiceId) } }
            }
        }

        /** Makes [voice] the default for books without a voice of their own. */
        fun select(voice: Voice) {
            viewModelScope.launch { settings.setDefaultVoice(voice.id) }
        }

        /** Speaks [sampleText] with [voice] so the listener can compare voices. */
        fun playSample(
            voice: Voice,
            sampleText: String,
        ) {
            _state.update { it.copy(preparingSampleId = voice.id, sampleFailed = false) }
            viewModelScope.launch {
                val file = audioCache.sampleFile(voice.id)
                val result = speechEngine.synthesize(sampleText, voice, file)
                _state.update { it.copy(preparingSampleId = null, sampleFailed = result !is SynthesisResult.Rendered) }
                if (result is SynthesisResult.Rendered) {
                    player().apply {
                        setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                        prepare()
                        play()
                    }
                }
            }
        }

        private fun player(): Player = samplePlayer ?: ExoPlayer.Builder(context).build().also { samplePlayer = it }

        override fun onCleared() {
            samplePlayer?.release()
            samplePlayer = null
        }

        private companion object {
            const val PORTUGUESE = "pt"
        }
    }
