package com.giovannimartins.rreader.ui.about

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.ui.assertAllTextReadableOn
import com.giovannimartins.rreader.ui.theme.Ink
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** ADR 0018: the app shows its licences, the GPL's reason and where its source code is. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val read = mutableListOf<AboutComponent>()
    private val links = mutableListOf<String>()

    @Test
    fun `shows the version, the address of the source code and why the app is under the GPL`() {
        show()

        compose.onNodeWithText("Version 1.2.3").assertExists()
        compose.onNodeWithText(AboutContent.SOURCE_URL).assertExists()
        compose.onNodeWithText("Open in the browser").performClick()
        compose.onNodeWithText("GNU General Public License, version 3", substring = true).assertExists()
        assertThat(links).containsExactly(AboutContent.SOURCE_URL)
    }

    @Test
    fun `every component names its licence and opens its text or its terms`() {
        show()

        scrollTo("Read the licence of piper-phonemize and espeak-ng")
        compose.onNodeWithText("GNU GPL 3.0").assertExists()
        compose.onNodeWithContentDescription("Read the licence of piper-phonemize and espeak-ng").performClick()
        scrollTo("Open the terms of ML Kit text recognition (Google)")
        compose.onNodeWithContentDescription("Open the terms of ML Kit text recognition (Google)").performClick()
        scrollTo("Read the licence of Atkinson Hyperlegible Next")

        assertThat(read.map { it.asset }).containsExactly("GPL-3.0.txt")
        assertThat(links).containsExactly("https://developers.google.com/ml-kit/terms")
    }

    @Test
    fun `lists the terms of every voice model`() {
        show()

        listOf(
            "Dora, Alex and Santa (Kokoro v1.0): Apache License 2.0.",
            "R Reader is free and non-commercial, which these terms allow.",
        ).forEach { text ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
            compose.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun `all text is readable on the dark background`() {
        show()

        compose.assertAllTextReadableOn(Ink)
    }

    @Test
    fun `a licence text is shown paragraph by paragraph`() {
        val gpl = AboutContent.components.first { it.asset == "GPL-3.0.txt" }
        compose.setContent {
            RReaderTheme {
                LicenceScreen(
                    licence = OpenLicence(gpl, listOf("GNU GENERAL PUBLIC LICENSE", "Preamble")),
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("GNU GPL 3.0").assertExists()
        compose.onNodeWithText("Preamble").assertExists()
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.assertAllTextReadableOn(Ink)
    }

    @Test
    fun `every licence text ships with the app and opens`() {
        val assets =
            (AboutContent.components + AboutContent.voiceTerms.map { it.second })
                .mapNotNull { it.asset }
                .toSet()
        assets.forEach { asset -> assertThat(File("src/main/assets/licenses/$asset").isFile).isTrue() }
        assertThat(File("src/main/assets/licenses/GPL-3.0.txt").readText()).contains("GNU GENERAL PUBLIC LICENSE")

        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel =
            AboutViewModel(LicenceTexts(ApplicationProvider.getApplicationContext<Context>(), Dispatchers.Unconfined))
        try {
            viewModel.read(AboutContent.components.first { it.asset == "GPL-3.0.txt" })
            val licence = runBlocking { viewModel.licence.first { it?.paragraphs?.isNotEmpty() == true } }
            assertThat(licence?.paragraphs?.first()).contains("GNU GENERAL PUBLIC LICENSE")
            viewModel.closeLicence()
            assertThat(viewModel.licence.value).isNull()
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun scrollTo(description: String) {
        compose
            .onNode(hasScrollAction())
            .performScrollToNode(
                androidx.compose.ui.test
                    .hasContentDescription(description),
            )
    }

    private fun show() {
        compose.setContent {
            RReaderTheme {
                AboutScreen(
                    version = "1.2.3",
                    onReadLicence = { read += it },
                    onOpenLink = { links += it },
                    onClose = {},
                )
            }
        }
    }
}
