package com.giovannimartins.rreader.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.ui.assertAllTextReadableOn
import com.giovannimartins.rreader.ui.theme.Ink
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full player on a common phone screen (about 384 × 832 dp, minus the system bars): everything must be
 * visible without scrolling, and all text must follow the theme. A device-test finding: controls were cut
 * off below the screen edge and some text was black on the dark background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class PlayerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `all text is readable on the dark background`() {
        show()

        compose.assertAllTextReadableOn(Ink)
    }

    @Test
    fun `every control fits on the screen without scrolling`() {
        show()

        listOf(
            compose.onNodeWithContentDescription("Minimize the player"),
            compose.onNodeWithContentDescription("Back 15 seconds"),
            compose.onNodeWithContentDescription("Pause"),
            compose.onNodeWithContentDescription("Forward 15 seconds"),
            compose.onNodeWithContentDescription("Speed: 1.25 times"),
            compose.onNodeWithText("Sleep timer", useUnmergedTree = true),
            compose.onNodeWithText("Chapters", useUnmergedTree = true),
            compose.onNodeWithText("Text", useUnmergedTree = true),
        ).forEach { it.assertFullyOnScreen() }
    }

    @Test
    fun `with larger system text the playback controls stay on the screen`() {
        show(fontScale = 1.3f)

        listOf("Back 15 seconds", "Pause", "Forward 15 seconds").forEach { label ->
            compose.onNodeWithContentDescription(label).assertFullyOnScreen()
        }
    }

    private fun SemanticsNodeInteraction.assertFullyOnScreen() {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = getUnclippedBoundsInRoot()
        assertWithMessage("bottom of ${fetchSemanticsNode().config}")
            .that(bounds.bottom.value)
            .isAtMost(screen.bottom.value)
        assertWithMessage("top of ${fetchSemanticsNode().config}").that(bounds.top.value).isAtLeast(screen.top.value)
    }

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RReaderTheme {
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
            }
        }
    }
}
