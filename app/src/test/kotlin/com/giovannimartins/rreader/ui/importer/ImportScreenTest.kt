package com.giovannimartins.rreader.ui.importer

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.ui.assertAllTextReadableIn
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.theme.RReaderDarkColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderLightColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The page that adds a book, opened from the library's "Add a book". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class ImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    @Test
    fun `explains how to add a book, with a large file button, the share tip and a way back`() {
        show(receiving = false)

        compose.onNode(hasText("Add a book") and isHeading()).assertExists()
        compose.onNode(hasText("Choose a file") and hasClickAction()).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Tip:", substring = true).assertExists()
        compose
            .onNodeWithContentDescription("Back to the library")
            .assertHeightIsAtLeast(56.dp)
            .assertWidthIsAtLeast(56.dp)
            .performClick()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `while a file arrives the button waits`() {
        show(receiving = true)

        compose.onNode(hasText("Choose a file") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `all text is readable in both themes`() {
        show(receiving = false, darkTheme = false)

        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    fun `all text is readable in the dark theme`() {
        show(receiving = false, darkTheme = true)

        compose.assertAllTextReadableIn(RReaderDarkColorScheme)
    }

    private fun show(
        receiving: Boolean,
        darkTheme: Boolean = true,
    ) {
        compose.setContent {
            RReaderTheme(darkTheme = darkTheme) {
                ScreenSurface { ImportScreen(receiving = receiving, onFileSelect = {}, onClose = { closed++ }) }
            }
        }
    }
}
