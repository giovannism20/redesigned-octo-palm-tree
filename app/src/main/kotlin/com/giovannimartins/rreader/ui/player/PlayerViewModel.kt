package com.giovannimartins.rreader.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.playback.ListeningSession
import com.giovannimartins.rreader.playback.ListeningStatus
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** State and actions of the mini player and the full player. */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val controls: PlaybackControls,
        status: ListeningStatus,
    ) : ViewModel() {
        /** The open book, or null when nothing is open (no player is shown). */
        val nowPlaying: StateFlow<NowPlaying?> = controls.nowPlaying

        /** The sleep timer and, while it counts down, the listening time left. */
        val sleepTimer: StateFlow<SleepTimerState> = status.sleepTimer

        /** The name of the voice chosen for the open book that could not start, so the phone's voice reads; or null. */
        val replacedVoice: StateFlow<String?> =
            status.session
                .map(::replacedVoiceName)
                .stateIn(viewModelScope, SharingStarted.Eagerly, replacedVoiceName(status.session.value))

        private fun replacedVoiceName(session: ListeningSession?): String? = session?.replacedVoice?.name

        /** The name of the voice that made listening pause because it is too slow on this phone; or null. */
        val slowVoice: StateFlow<String?> =
            status.session
                .map { it?.slowVoice?.name }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    status.session.value
                        ?.slowVoice
                        ?.name,
                )

        /** Pauses when listening, otherwise starts listening again (also after a problem or at the end). */
        fun togglePlayPause() {
            if (nowPlaying.value?.isPlaying == true) controls.pause() else controls.resume()
        }

        fun seekBack() = controls.seekBack()

        fun seekForward() = controls.seekForward()

        fun seekTo(positionMs: Long) = controls.seekTo(positionMs)

        fun playChapter(index: Int) = controls.playChapter(index)

        fun setSpeed(speed: Float) = controls.setSpeed(speed)

        /** Tries again after a problem. */
        fun retry() = controls.resume()

        fun setSleepTimer(setting: SleepTimerSetting) = controls.setSleepTimer(setting)

        fun cancelSleepTimer() = controls.cancelSleepTimer()
    }
