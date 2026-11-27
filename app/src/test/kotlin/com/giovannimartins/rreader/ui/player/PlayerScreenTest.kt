package com.giovannimartins.rreader.ui.player

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `choosing a speed applies it`() {
        show(previewNowPlaying())

        compose.onNodeWithContentDescription("Speed: 1.25 times").performScrollTo().performClick()
        compose.onNodeWithText("1.5×").performClick()

        assertThat(events).containsExactly("speed 1.5")
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

    private fun show(nowPlaying: NowPlaying) {
        compose.setContent {
            RReaderTheme {
                PlayerScreen(
                    nowPlaying = nowPlaying,
                    onMinimize = { events += "minimize" },
                    onTogglePlayPause = { events += "toggle" },
                    onSeekBack = { events += "back" },
                    onSeekForward = { events += "forward" },
                    onSeekTo = { events += "seek $it" },
                    onSelectChapter = { events += "chapter $it" },
                    onSpeedChange = { events += "speed $it" },
                    onRetry = { events += "retry" },
                    onOpenVoices = { events += "voices" },
                )
            }
        }
    }
}
