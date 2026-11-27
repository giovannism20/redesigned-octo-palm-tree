package com.giovannimartins.rreader.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerViewModelTest {
    private val controls = RecordingControls()
    private val viewModel = PlayerViewModel(controls)

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
}
