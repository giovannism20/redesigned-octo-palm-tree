package com.giovannimartins.rreader.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.ui.assertAllTextReadableIn
import com.giovannimartins.rreader.ui.assertAllTextReadableOn
import com.giovannimartins.rreader.ui.theme.Ink
import com.giovannimartins.rreader.ui.theme.RReaderDarkColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderLightColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full player on a common phone screen (about 384 × 832 dp, minus the system bars): everything must be
 * visible without scrolling, and all text must follow the theme. A device-test finding: controls were cut
 * off below the screen edge and some text was black on the dark background. At the largest text size the
 * playback controls stay in view and everything else can be scrolled to. Native graphics, so text is measured
 * with the real typeface and wraps as on a phone (the default fakes text widths).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h780dp")
class PlayerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `all text is readable on the dark background`() {
        show(replacedVoice = "Dora")

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
            compose.onNodeWithText("Voice", useUnmergedTree = true),
        ).forEach { it.assertFullyOnScreen() }
    }

    @Test
    fun `all text is readable in the light theme, the voice notice included`() {
        show(darkTheme = false, replacedVoice = "Dora")

        compose.onNodeWithText("Dora", substring = true).assertExists()
        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    fun `the slow-voice notice is readable in both themes and offers a faster voice`() {
        show(slowVoice = "Dora")

        compose.onNodeWithText("Dora", substring = true).assertExists()
        compose.onNodeWithText("Choose a faster voice").assertExists()
        compose.assertAllTextReadableIn(RReaderDarkColorScheme)
    }

    @Test
    fun `the slow-voice notice is readable in the light theme`() {
        show(darkTheme = false, slowVoice = "Dora")

        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    fun `with larger system text the playback controls stay on the screen`() {
        show(fontScale = 1.3f)

        listOf("Back 15 seconds", "Pause", "Forward 15 seconds").forEach { label ->
            compose.onNodeWithContentDescription(label).assertFullyOnScreen()
        }
    }

    @Test
    fun `at the largest text size on larger system text the playback controls stay on the screen`() {
        show(fontScale = 1.3f, textSize = TextSize.LARGEST)

        listOf("Back 15 seconds", "Pause", "Forward 15 seconds").forEach { label ->
            compose.onNodeWithContentDescription(label).assertFullyOnScreen()
        }
    }

    @Test
    fun `at the largest text size nothing is cut off and every other control scrolls into view`() {
        show(fontScale = 1.3f, textSize = TextSize.LARGEST)

        listOf(
            compose.onNodeWithContentDescription("Minimize the player"),
            compose.onNodeWithContentDescription("Speed: 1.25 times"),
            compose.onNodeWithContentDescription("Sleep timer"),
            compose.onNode(hasText("Chapters") and hasClickAction()),
            compose.onNode(hasText("Text") and hasClickAction()),
            compose.onNode(hasText("Voice") and hasClickAction()),
        ).forEach { control ->
            control.performScrollTo()
            control.assertFullyOnScreen()
        }
    }

    @Test
    fun `at the largest text size the cover stays between the top bar and the title, or gives way`() {
        show(fontScale = 1.3f, textSize = TextSize.LARGEST)
        assertCoverInItsPlace()
    }

    @Test
    fun `at the normal size the cover is shown in its place`() {
        show()
        assertThat(coverTitles().fetchSemanticsNodes()).isNotEmpty()
        assertCoverInItsPlace()
    }

    /** The generated cover's title, drawn inside the cover (the book's title below it is a heading). */
    private fun coverTitles() = compose.onAllNodes(hasText("Dom Casmurro") and !isHeading(), useUnmergedTree = true)

    private fun assertCoverInItsPlace() {
        val topBar = compose.onNodeWithContentDescription("Minimize the player").getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("Dom Casmurro") and isHeading()).getUnclippedBoundsInRoot()
        coverTitles().fetchSemanticsNodes().indices.forEach { index ->
            val cover = coverTitles()[index].getUnclippedBoundsInRoot()
            assertWithMessage("cover top").that(cover.top.value).isAtLeast(topBar.bottom.value)
            assertWithMessage("cover bottom").that(cover.bottom.value).isAtMost(title.top.value)
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

    private fun show(
        fontScale: Float = 1f,
        textSize: TextSize = TextSize.NORMAL,
        darkTheme: Boolean = true,
        replacedVoice: String? = null,
        slowVoice: String? = null,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RReaderTheme(darkTheme = darkTheme, textSize = textSize) {
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
                        replacedVoice = replacedVoice,
                        slowVoice = slowVoice,
                    )
                }
            }
        }
    }
}
