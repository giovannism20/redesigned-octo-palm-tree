package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import com.giovannimartins.rreader.ui.components.AppAlertDialog
import com.giovannimartins.rreader.ui.fontScaleOfText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The theme at the app's root: which colours each theme choice shows, and the text size on top of the phone's. */
@RunWith(RobolectricTestRunner::class)
class RReaderThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "notnight")
    fun `dark and light are what they say, and the phone's choice follows a phone in light mode`() {
        val backgrounds = backgrounds()

        assertThat(backgrounds[ThemeChoice.DARK]).isEqualTo(RReaderDarkColorScheme.background)
        assertThat(backgrounds[ThemeChoice.LIGHT]).isEqualTo(RReaderLightColorScheme.background)
        assertThat(backgrounds[ThemeChoice.PHONE]).isEqualTo(RReaderLightColorScheme.background)
    }

    @Test
    @Config(qualifiers = "night")
    fun `the phone's choice follows a phone in dark mode`() {
        val backgrounds = backgrounds()

        assertThat(backgrounds[ThemeChoice.PHONE]).isEqualTo(RReaderDarkColorScheme.background)
        assertThat(backgrounds[ThemeChoice.LIGHT]).isEqualTo(RReaderLightColorScheme.background)
    }

    @Test
    fun `the text size multiplies the phone's font scale for everything inside the theme`() {
        val seen = mutableMapOf<TextSize, Float>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, PHONE_SCALE)) {
                TextSize.entries.forEach { size ->
                    RReaderTheme(textSize = size) { seen[size] = LocalDensity.current.fontScale }
                }
            }
        }
        compose.waitForIdle()

        TextSize.entries.forEach { size -> assertThat(seen[size]).isWithin(TOLERANCE).of(PHONE_SCALE * size.scale) }
        assertThat(TextSize.entries.map { it.scale }).isInStrictOrder()
    }

    @Test
    fun `the app's dialog keeps the text size, which Material's own dialog window loses`() {
        compose.setContent {
            RReaderTheme(textSize = TextSize.LARGEST) {
                AppAlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("App") }, text = {})
                AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Material") })
            }
        }

        assertThat(compose.onNodeWithText("App", useUnmergedTree = true).fontScaleOfText())
            .isEqualTo(TextSize.LARGEST.scale)
        // Its window brings the phone's own size back.
        assertThat(compose.onNodeWithText("Material", useUnmergedTree = true).fontScaleOfText()).isEqualTo(1f)
    }

    /** The background each theme choice shows (Material copies the scheme it is given, so its roles are compared). */
    private fun backgrounds(): Map<ThemeChoice, Color> {
        val seen = mutableMapOf<ThemeChoice, Color>()
        compose.setContent {
            ThemeChoice.entries.forEach { choice ->
                RReaderTheme(darkTheme = choice.isDark()) { seen[choice] = MaterialTheme.colorScheme.background }
            }
        }
        compose.waitForIdle()
        return seen
    }

    private companion object {
        const val PHONE_SCALE = 1.3f
        const val TOLERANCE = 0.0001f
    }
}
