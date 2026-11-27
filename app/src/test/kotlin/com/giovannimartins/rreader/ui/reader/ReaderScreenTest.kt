package com.giovannimartins.rreader.ui.reader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.ui.assertAllTextReadableIn
import com.giovannimartins.rreader.ui.assertAllTextReadableOn
import com.giovannimartins.rreader.ui.player.previewNowPlaying
import com.giovannimartins.rreader.ui.textLayout
import com.giovannimartins.rreader.ui.theme.AppFontFamily
import com.giovannimartins.rreader.ui.theme.Ink
import com.giovannimartins.rreader.ui.theme.LiterataFontFamily
import com.giovannimartins.rreader.ui.theme.RReaderLightColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a phone-sized screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val paragraphs =
        listOf(
            "Ia entrar na sala de visitas, quando ouvi proferir o meu nome.",
            "Escondi-me atrás da porta.",
        )
    private val reading =
        ReaderUiState(
            chapterIndex = 2,
            chapterTitle = "Capítulo III — A denúncia",
            paragraphs = paragraphs,
            highlight = ReaderHighlight(paragraph = 1, start = 0, end = paragraphs[1].length),
        )

    @Test
    fun `every control is spoken and large enough to touch`() {
        show(reading)

        val labels =
            listOf(
                "Back to the player",
                "Smaller text",
                "Larger text",
                "Pause",
                "Back 15 seconds",
                "Forward 15 seconds",
            )
        labels.forEach { label ->
            compose
                .onNodeWithContentDescription(label)
                .assertHeightIsAtLeast(56.dp)
                .assertWidthIsAtLeast(56.dp)
                .performClick()
        }

        assertThat(events).containsExactly("close", "smaller", "larger", "toggle", "back", "forward").inOrder()
    }

    @Test
    fun `shows the chapter title and its paragraphs, marking the one being read`() {
        show(reading)

        compose.onNode(hasText("Capítulo III — A denúncia") and isHeading()).assertExists()
        compose.onNodeWithText(paragraphs[0]).assertHeightIsAtLeast(48.dp)
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Being read now"))
            .assertExists()
        compose.onNodeWithText("Back to the reading").assertDoesNotExist()
    }

    @Test
    fun `tapping a paragraph reads from the sentence there, and TalkBack can read from a paragraph`() {
        show(reading)

        compose.onNodeWithText(paragraphs[0]).performClick()
        compose.onNodeWithText(paragraphs[1]).performSemanticsAction(SemanticsActions.OnClick)

        assertThat(events).hasSize(2)
        assertThat(events[0]).startsWith("read 0:")
        assertThat(events[1]).isEqualTo("read 1:0")
        val label =
            compose
                .onNodeWithText(paragraphs[1])
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .label
        assertThat(label).isEqualTo("Read from here")
    }

    @Test
    fun `after scrolling away a large button goes back to the reading`() {
        show(reading.copy(following = false))

        compose.onNodeWithText("Back to the reading").assertHeightIsAtLeast(56.dp).performClick()

        assertThat(events).containsExactly("back to reading")
    }

    @Test
    fun `text sizes stop at their ends`() {
        show(reading.copy(textScale = 2f))

        compose.onNodeWithContentDescription("Larger text").assertIsNotEnabled()
    }

    @Test
    fun `a chapter that is opening is announced`() {
        show(reading.copy(paragraphs = null))

        compose.onNodeWithContentDescription("Opening the chapter…").assertExists()
    }

    @Test
    fun `all text is readable on the dark background`() {
        show(reading)

        compose.assertAllTextReadableOn(Ink)
    }

    @Test
    fun `all text is readable in the light theme, the highlight included`() {
        show(reading.copy(readingFont = ReadingFont.LITERATA), darkTheme = false)

        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    fun `the text is in the reading font the listener chose, and nothing else is`() {
        show(reading.copy(readingFont = ReadingFont.LITERATA))

        assertThat(fontOf(paragraphs[0])).isEqualTo(LiterataFontFamily)
        assertThat(fontOf("Capítulo III — A denúncia")).isEqualTo(AppFontFamily)
    }

    @Test
    fun `the app's typeface is the reading font until another is chosen`() {
        show(reading)

        assertThat(fontOf(paragraphs[0])).isEqualTo(AppFontFamily)
    }

    @Test
    fun `a chapter that cannot be read says so`() {
        show(reading.copy(paragraphs = null, failed = true))

        compose.onNodeWithText("This chapter could not be opened.").assertExists()
    }

    private fun fontOf(text: String) =
        compose
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput.style.fontFamily

    private fun show(
        state: ReaderUiState,
        darkTheme: Boolean = true,
    ) {
        compose.setContent {
            RReaderTheme(darkTheme = darkTheme) {
                ReaderScreen(
                    state = state,
                    nowPlaying = previewNowPlaying(),
                    onClose = { events += "close" },
                    onShrinkText = { events += "smaller" },
                    onEnlargeText = { events += "larger" },
                    onUserScroll = { events += "scroll" },
                    onBackToReading = { events += "back to reading" },
                    onReadFrom = { paragraph, offset -> events += "read $paragraph:$offset" },
                    onTogglePlayPause = { events += "toggle" },
                    onSeekBack = { events += "back" },
                    onSeekForward = { events += "forward" },
                )
            }
        }
    }
}
