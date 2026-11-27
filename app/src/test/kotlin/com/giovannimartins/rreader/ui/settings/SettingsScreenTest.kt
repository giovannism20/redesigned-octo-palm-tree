package com.giovannimartins.rreader.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import com.giovannimartins.rreader.ui.assertAllTextReadableIn
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.textLayout
import com.giovannimartins.rreader.ui.theme.AppFontFamily
import com.giovannimartins.rreader.ui.theme.LiterataFontFamily
import com.giovannimartins.rreader.ui.theme.RReaderDarkColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderLightColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Ajustes tab on a phone: radio rows TalkBack reads, large targets, the choice shown by more than colour. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    @Test
    fun `every option is a radio button with its name, large enough to touch, and choosing one sends it`() {
        show(Appearance())

        val options =
            listOf(
                "Normal" to "size NORMAL",
                "Large" to "size LARGE",
                "Larger" to "size LARGER",
                "Very large" to "size LARGEST",
                "Dark" to "theme DARK",
                "Light" to "theme LIGHT",
                "Same as the phone" to "theme PHONE",
                "Atkinson Hyperlegible" to "font ATKINSON",
                "Literata" to "font LITERATA",
            )
        options.forEach { (label, _) ->
            val option = radio(label)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label, substring = true))
            option.assertHeightIsAtLeast(56.dp).performClick()
        }

        assertThat(events).containsExactlyElementsIn(options.map { it.second }).inOrder()
    }

    @Test
    @Config(qualifiers = "w384dp-h2400dp")
    fun `the chosen options are selected for TalkBack and marked on screen, one per group`() {
        show(Appearance(TextSize.LARGER, ThemeChoice.LIGHT, ReadingFont.LITERATA))

        radio("Larger").assertIsSelected()
        radio("Normal").assertIsNotSelected()
        radio("Light").assertIsSelected()
        radio("Dark").assertIsNotSelected()
        radio("Literata").assertIsSelected()
        radio("Atkinson Hyperlegible").assertIsNotSelected()
        // Besides the selection colour: a check mark on each chosen row, and on no other.
        val marks = hasTestTag(CHOSEN_MARK_TAG)
        assertThat(compose.onAllNodes(marks and hasAnyAncestor(isSelected()), true).fetchSemanticsNodes()).hasSize(3)
        assertThat(compose.onAllNodes(marks and hasAnyAncestor(isNotSelected()), true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `sections have headings and the preview shows a sentence and a button at the chosen size`() {
        show(Appearance())

        listOf("Settings", "Text size", "Theme").forEach { compose.onNode(hasText(it) and isHeading()).assertExists() }
        compose.onNodeWithText("This is how large the letters of the app are.", substring = true).assertExists()
        compose.onNodeWithText("Listen", substring = true).assertExists()
    }

    @Test
    fun `each reading font shows its sample in its own typeface`() {
        show(Appearance())

        val sample = "It was the best of times, it was the worst of times."
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Literata", substring = true))
        val families =
            compose
                .onAllNodes(hasText(sample), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .indices
                .map { index ->
                    compose
                        .onAllNodes(hasText(sample), useUnmergedTree = true)[index]
                        .textLayout()
                        .layoutInput.style.fontFamily
                }
        assertThat(families).containsExactly(AppFontFamily, LiterataFontFamily).inOrder()
    }

    @Test
    fun `all text is readable in the dark theme`() {
        show(Appearance(theme = ThemeChoice.DARK, readingFont = ReadingFont.LITERATA), darkTheme = true)

        compose.assertAllTextReadableIn(RReaderDarkColorScheme)
    }

    @Test
    fun `all text is readable in the light theme`() {
        show(Appearance(theme = ThemeChoice.LIGHT, textSize = TextSize.LARGE), darkTheme = false)

        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w384dp-h780dp")
    fun `speaks Portuguese`() {
        show(Appearance())

        listOf("Ajustes", "Tamanho do texto", "Normal", "Grande", "Maior", "Muito grande").forEach { label ->
            compose.onNode(hasText(label) and (isHeading() or isSelectable())).assertExists()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Igual ao celular", substring = true))
        listOf("Escuro", "Claro", "Igual ao celular").forEach { label -> radio(label).assertExists() }
    }

    /** The radio row named [label]. */
    private fun radio(label: String) =
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and hasText(label))

    private fun show(
        appearance: Appearance,
        darkTheme: Boolean = true,
    ) {
        compose.setContent {
            // The app shell draws the tabs on the screen surface.
            RReaderTheme(darkTheme = darkTheme) {
                ScreenSurface {
                    SettingsScreen(
                        appearance = appearance,
                        onTextSizeChange = { events += "size $it" },
                        onThemeChange = { events += "theme $it" },
                        onReadingFontChange = { events += "font $it" },
                    )
                }
            }
        }
    }
}
