package com.giovannimartins.rreader.ui.player

import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [PlaybackControls] that record every call and expose a settable [state]. */
internal class RecordingControls : PlaybackControls {
    val state = MutableStateFlow<NowPlaying?>(null)
    val calls = mutableListOf<String>()
    override val nowPlaying: StateFlow<NowPlaying?> = state

    override fun play(bookId: String) {
        calls += "play $bookId"
    }

    override fun resume() {
        calls += "resume"
    }

    override fun pause() {
        calls += "pause"
    }

    override fun seekBack() {
        calls += "seekBack"
    }

    override fun seekForward() {
        calls += "seekForward"
    }

    override fun seekTo(positionMs: Long) {
        calls += "seekTo $positionMs"
    }

    override fun playChapter(index: Int) {
        calls += "playChapter $index"
    }

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
    }

    override fun readFrom(position: ReadingPosition) {
        calls += "readFrom ${position.chapterIndex}:${position.charOffset}"
    }

    override fun setSleepTimer(setting: SleepTimerSetting) {
        calls += "sleepTimer $setting"
    }

    override fun cancelSleepTimer() {
        calls += "cancelSleepTimer"
    }

    override fun refreshVoice() {
        calls += "refreshVoice"
    }
}
