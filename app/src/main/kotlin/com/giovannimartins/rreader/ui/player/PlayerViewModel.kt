package com.giovannimartins.rreader.ui.player

import androidx.lifecycle.ViewModel
import com.giovannimartins.rreader.playback.ListeningStatus
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
