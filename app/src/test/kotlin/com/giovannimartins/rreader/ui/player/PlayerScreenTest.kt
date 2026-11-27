package com.giovannimartins.rreader.ui.player

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** On a phone-sized screen; the full player scrolls, so nodes are scrolled into view before clicks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    @Test
    fun `the main controls are large and spoken`() {
        show(previewNowPlaying())

        listOf("Pause", "Back 15 seconds", "Forward 15 seconds", "Minimize the player").forEach { label ->
            compose
                .onNodeWithContentDescription(label)
                .assertHeightIsAtLeast(56.dp)
                .assertWidthIsAtLeast(56.dp)
        }
        compose.onNodeWithContentDescription("Pause").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back 15 seconds").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Forward 15 seconds").performScrollTo().performClick()

        assertThat(events).containsExactly("toggle", "back", "forward").inOrder()
    }

    @Test
    fun `shows the book, the chapter and the times`() {
        show(previewNowPlaying())

        compose.onNode(hasText("Dom Casmurro") and isHeading()).assertExists()
        compose.onNodeWithText("Machado de Assis").assertExists()
        compose.onNodeWithText("Capítulo III — A denúncia").assertExists()
        compose.onNodeWithText("Chapter 3 of 3").assertExists()
        compose.onNodeWithText("1:35").assertExists()
        compose.onNodeWithContentDescription("5:17 left").assertExists()
    }

    @Test
    fun `shows the time left in the chapter and in the book at the current speed`() {
        show(previewNowPlaying())

        compose.onNodeWithText("4 min left in this chapter").assertExists()
        compose.onNodeWithText("2 h 44 min left in the book").assertExists()
    }

    @Test
    fun `swiping down minimizes the player`() {
        show(previewNowPlaying())

        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }

        assertThat(events).containsExactly("minimize")
    }

    @Test
    fun `a short slow pull springs back without minimizing`() {
        show(previewNowPlaying())

        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = centerY + 60f, durationMillis = 1_000) }
        compose.waitForIdle()

        assertThat(events).isEmpty()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `choosing a speed applies it`() {
        show(previewNowPlaying())

        compose.onNodeWithContentDescription("Speed: 1.25 times").performScrollTo().performClick()
        compose.onNodeWithText("1.5×").performClick()

        assertThat(events).containsExactly("speed 1.5")
    }

    @Test
    fun `the sleep timer offers durations and the end of the chapter`() {
        show(previewNowPlaying())

        compose
            .onNodeWithContentDescription(
                "Sleep timer",
            ).assertHeightIsAtLeast(56.dp)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Stop listening after").assertExists()
        listOf("5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Turn off the timer").assertDoesNotExist()
        compose.onNodeWithText("30 minutes").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Sleep timer").performScrollTo().performClick()
        compose.onNodeWithText("End of chapter").performClick()

        assertThat(events).containsExactly("sleep After(duration=30m)", "sleep EndOfChapter").inOrder()
    }

    @Test
    fun `a running sleep timer shows its countdown and can be turned off`() {
        show(previewNowPlaying(), SleepTimerState.After(29.minutes + 59.seconds))

        compose.onNodeWithText("29:59").assertExists()
        compose.onNodeWithContentDescription("Sleep timer: stops in 29:59").performScrollTo().performClick()
        compose.onNodeWithText("Turn off the timer").performClick()

        assertThat(events).containsExactly("cancelSleepTimer")
    }

    @Test
    fun `a timer for the end of the chapter says so`() {
        show(previewNowPlaying(), SleepTimerState.EndOfChapter)

        compose.onNodeWithContentDescription("Sleep timer: stops at the end of the chapter").assertExists()
    }

    @Test
    fun `the text button opens the follow-along reader`() {
        show(previewNowPlaying())

        compose
            .onNodeWithText("Text")
            .assertHeightIsAtLeast(56.dp)
            .performScrollTo()
            .performClick()

        assertThat(events).containsExactly("reader")
    }

    @Test
    fun `choosing a chapter starts it`() {
        show(previewNowPlaying())

        compose.onNodeWithText("Chapters").performScrollTo().performClick()
        compose.onNodeWithText("Capítulo II — Do livro").performClick()

        assertThat(events).containsExactly("chapter 1")
    }

    @Test
    fun `without a voice it explains and offers the voices`() {
        show(previewNowPlaying().copy(isPlaying = false, problem = PlaybackProblem.NO_VOICE))

        compose.onNodeWithText("No Portuguese voice is installed. Install one to listen to this book.").assertExists()
        compose.onNodeWithText("Open voices").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Play").assertExists()

        assertThat(events).containsExactly("voices")
    }

    @Test
    fun `the mini player opens the player and controls playback`() {
        compose.setContent {
            RReaderTheme {
                MiniPlayer(
                    nowPlaying = previewNowPlaying(),
                    onOpen = { events += "open" },
                    onTogglePlayPause = { events += "toggle" },
                    onSeekBack = { events += "back" },
                )
            }
        }

        compose.onNodeWithText("Dom Casmurro").performClick()
        compose.onNodeWithContentDescription("Back 15 seconds").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Pause").assertHeightIsAtLeast(56.dp).performClick()

        assertThat(events).containsExactly("open", "back", "toggle").inOrder()
    }

    private fun show(
        nowPlaying: NowPlaying,
        sleepTimer: SleepTimerState = SleepTimerState.Off,
    ) {
        compose.setContent {
            RReaderTheme {
                PlayerScreen(
                    nowPlaying = nowPlaying,
                    sleepTimer = sleepTimer,
                    onMinimize = { events += "minimize" },
                    onTogglePlayPause = { events += "toggle" },
                    onSeekBack = { events += "back" },
                    onSeekForward = { events += "forward" },
                    onSeekTo = { events += "seek $it" },
                    onSelectChapter = { events += "chapter $it" },
                    onSpeedChange = { events += "speed $it" },
                    onSetSleepTimer = { events += "sleep $it" },
                    onCancelSleepTimer = { events += "cancelSleepTimer" },
                    onRetry = { events += "retry" },
                    onOpenVoices = { events += "voices" },
                    onOpenReader = { events += "reader" },
                )
            }
        }
    }
}
