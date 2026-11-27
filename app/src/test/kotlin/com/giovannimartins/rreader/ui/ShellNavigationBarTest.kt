package com.giovannimartins.rreader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.giovannimartins.rreader.data.settings.TextSize
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
 * The bottom bar on a common phone width (384 dp) with the phone's font at 1.3 and the app's largest text size: four
 * tabs, every label whole on one line inside its tab, and never smaller than 16 sp at the phone's normal size. Native
 * graphics, so text is measured with the real typeface (the default fakes text widths).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h780dp")
class ShellNavigationBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<TopDestination>()

    @Test
    fun `four tabs, and Add a book is no longer one of them`() {
        show(TextSize.NORMAL, phoneScale = 1f)

        assertThat(tabLabels()).containsExactly("Library", "Explore", "Voices", "Settings").inOrder()
        compose.onNodeWithText("Settings").performClick()
        assertThat(chosen).containsExactly(TopDestination.SETTINGS)
    }

    @Test
    fun `at the largest size every label fits its tab on one line`() {
        show(TextSize.LARGEST, phoneScale = PHONE_SCALE)

        listOf("Library", "Explore", "Voices", "Settings").forEach(::assertFits)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w384dp-h780dp")
    fun `at the largest size every Portuguese label fits its tab on one line`() {
        show(TextSize.LARGEST, phoneScale = PHONE_SCALE)

        listOf("Biblioteca", "Explorar", "Vozes", "Ajustes").forEach(::assertFits)
    }

    @Test
    fun `labels keep the listener's size while they fit`() {
        show(TextSize.LARGE, phoneScale = 1f)

        val label = compose.onNode(hasText("Voices") and hasAnyAncestor(isTab()), useUnmergedTree = true)
        assertThat(label.fontScaleOfText()).isWithin(TOLERANCE).of(TextSize.LARGE.scale)
    }

    @Test
    fun `the label scale goes down only as far as needed, and never below the floor`() {
        // A text 100 px wide above a scale of about 1.5 and 80 px below: it fits 90 px from about 1.5 down.
        val width = { scale: Float -> if (scale > 1.505f) 100f else 80f }

        assertThat(largestFittingScale(current = 1.95f, floor = 1f, available = 90f, widthAt = width))
            .isWithin(STEP_TOLERANCE)
            .of(1.5f)
        assertThat(largestFittingScale(current = 1.95f, floor = 1f, available = 50f, widthAt = width)).isEqualTo(1f)
        assertThat(largestFittingScale(current = 0.9f, floor = 0.9f, available = 1f, widthAt = width)).isEqualTo(0.9f)
    }

    private fun assertFits(label: String) {
        val text = compose.onNode(hasText(label) and hasAnyAncestor(isTab()), useUnmergedTree = true)
        val layout = text.textLayout()
        assertWithMessage("lines of $label").that(layout.lineCount).isEqualTo(1)
        assertWithMessage("ellipsis in $label").that(layout.isLineEllipsized(0)).isFalse()
        // The whole word on one line is no wider than the room its tab gives the label.
        assertWithMessage("width of $label")
            .that(layout.multiParagraph.maxIntrinsicWidth)
            .isAtMost(
                layout.layoutInput.constraints.maxWidth
                    .toFloat(),
            )
        assertWithMessage("width of $label")
            .that(layout.size.width)
            .isAtMost(tab(label).fetchSemanticsNode().size.width)
        assertWithMessage("style size of $label").that(layout.layoutInput.style.fontSize.value).isAtLeast(MIN_SP)
        assertWithMessage("scale of $label").that(layout.layoutInput.density.fontScale).isAtLeast(1f)
    }

    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(isTab() and hasText(label))

    private fun tabLabels(): List<String> =
        compose
            .onAllNodes(isTab())
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    /** A tab of the bar: Material marks each one as a selectable item of the bar's group. */
    private fun isTab() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private fun show(
        textSize: TextSize,
        phoneScale: Float,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, phoneScale)) {
                RReaderTheme(textSize = textSize) {
                    ShellNavigationBar(destination = TopDestination.LIBRARY, onDestinationSelect = { chosen += it })
                }
            }
        }
    }

    private companion object {
        const val PHONE_SCALE = 1.3f
        const val MIN_SP = 16f
        const val TOLERANCE = 0.001f

        /** One step of the search, which goes down by hundredths. */
        const val STEP_TOLERANCE = 0.006f
    }
}
