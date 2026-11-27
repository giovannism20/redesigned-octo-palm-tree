package com.giovannimartins.rreader.ui.player

import com.giovannimartins.rreader.playback.ListeningSession
import com.giovannimartins.rreader.playback.ListeningStatus
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.playback.voice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val controls = RecordingControls()
    private val status = ListeningStatus()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = PlayerViewModel(controls, status)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the main button pauses while listening and resumes otherwise`() {
        controls.state.value = previewNowPlaying().copy(isPlaying = true)
        viewModel.togglePlayPause()
        controls.state.value = previewNowPlaying().copy(isPlaying = false)
        viewModel.togglePlayPause()
        controls.state.value = null
        viewModel.togglePlayPause()

        assertThat(controls.calls).containsExactly("pause", "resume", "resume").inOrder()
    }

    @Test
    fun `controls go to the playback connection`() {
        viewModel.seekBack()
        viewModel.seekForward()
        viewModel.seekTo(1_000)
        viewModel.playChapter(2)
        viewModel.setSpeed(1.75f)
        viewModel.retry()

        assertThat(controls.calls)
            .containsExactly("seekBack", "seekForward", "seekTo 1000", "playChapter 2", "speed 1.75", "resume")
            .inOrder()
    }

    @Test
    fun `the sleep timer is set and turned off through the playback connection, and its state is shown`() {
        viewModel.setSleepTimer(SleepTimerSetting.After(30.minutes))
        viewModel.setSleepTimer(SleepTimerSetting.EndOfChapter)
        viewModel.cancelSleepTimer()
        status.publishSleepTimer(SleepTimerState.After(29.minutes))

        assertThat(controls.calls)
            .containsExactly("sleepTimer After(duration=30m)", "sleepTimer EndOfChapter", "cancelSleepTimer")
            .inOrder()
        assertThat(viewModel.sleepTimer.value).isEqualTo(SleepTimerState.After(29.minutes))
    }

    @Test
    fun `names the voice the phone voice replaces in the open book`() {
        assertThat(viewModel.replacedVoice.value).isNull()

        status.publishSession(ListeningSession("book", voice(id = "phone"), false, voice(id = "neural:dora")))

        assertThat(viewModel.replacedVoice.value).isEqualTo("neural:dora")
    }
}
