package com.giovannimartins.rreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.library.LibraryScreen
import com.giovannimartins.rreader.ui.library.LibraryUiState
import com.giovannimartins.rreader.ui.player.PlayerScreen
import com.giovannimartins.rreader.ui.player.previewNowPlaying
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.giovannimartins.rreader.ui.voices.VoicesScreen
import com.giovannimartins.rreader.ui.voices.VoicesUiState
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dialogs, bottom sheets and menus open in windows of their own, which bring the phone's density back: each must
 * apply the listener's text size again, or its text would be smaller than the screen it opened from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class TextSizeInWindowsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the library's removal dialog`() {
        showLargest {
            LibraryScreen(
                state = LibraryUiState.Content(continueListening = null, books = listOf(book())),
                onAddBook = {},
                onPlay = {},
                onRemove = {},
                onRetry = {},
            )
        }

        compose.onNodeWithText("Remove").performClick()

        assertLargest("Remove this book?")
        assertLargest("The book and your progress will be deleted from this phone.")
        assertLargest("Cancel")
    }

    @Test
    fun `the voices' delete and mobile data dialogs`() {
        val dora = NeuralVoices.all.first()
        showLargest { Voices(VoicesUiState(askDelete = dora, askMobileData = dora)) }

        assertLargest("Delete the voice Dora?")
        assertLargest("Download with mobile data?")
        assertLargest("Wait for Wi-Fi")
    }

    @Test
    fun `the player's chapter list`() {
        showLargest { Player() }

        compose.onNodeWithText("Chapters").performClick()

        compose.onNode(hasText("Chapters") and isHeading()).assertExists()
        assertLargest("Capítulo I — Do título")
    }

    @Test
    fun `the player's speed and sleep timer menus`() {
        showLargest { Player() }

        compose.onNodeWithContentDescription("Speed: 1.25 times").performClick()
        assertLargest("1.75×")
        compose.onNodeWithText("1.75×").performClick()
        compose.onNodeWithText("Sleep timer").performClick()
        assertLargest("Stop listening after")
        assertLargest("End of chapter")
    }

    @Test
    fun `the player's voice chooser`() {
        showLargest { Player() }

        compose.onNodeWithText("Voice").performClick()

        assertLargest("Choose the voice")
        assertLargest("Download more voices")
    }

    private fun assertLargest(text: String) {
        val scale = compose.onNodeWithText(text, useUnmergedTree = true).fontScaleOfText()
        assertWithMessage("font scale of \"$text\"").that(scale).isEqualTo(TextSize.LARGEST.scale)
    }

    private fun showLargest(content: @Composable () -> Unit) {
        compose.setContent { RReaderTheme(textSize = TextSize.LARGEST) { ScreenSurface { content() } } }
    }

    @Composable
    private fun Player() {
        PlayerScreen(
            nowPlaying = previewNowPlaying(),
            sleepTimer = SleepTimerState.Off,
            onMinimize = {},
            onTogglePlayPause = {},
            onSeekBack = {},
            onSeekForward = {},
            onSeekTo = {},
            onSelectChapter = {},
            onSpeedChange = {},
            onSetSleepTimer = {},
            onCancelSleepTimer = {},
            onRetry = {},
            onOpenVoices = {},
            onOpenReader = {},
        )
    }

    @Composable
    private fun Voices(state: VoicesUiState) {
        VoicesScreen(
            state = state,
            onToggleSample = {},
            onTogglePhoneSample = {},
            onDownload = {},
            onCancelDownload = {},
            onUse = {},
            onDelete = {},
            onConfirmDelete = {},
            onDismissDelete = {},
            onAnswerMobileData = {},
            onOpenSystemSettings = {},
            onOpenAbout = {},
        )
    }

    private fun book() =
        LibraryBook(
            id = "1",
            title = "Dom Casmurro",
            authors = listOf("Machado de Assis"),
            status = BookStatus.READY,
            failure = null,
            coverFile = null,
            charsRead = 40,
            totalChars = 100,
        )
}
