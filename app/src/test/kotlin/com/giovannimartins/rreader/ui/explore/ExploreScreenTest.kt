package com.giovannimartins.rreader.ui.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.catalog.DOM_CASMURRO
import com.giovannimartins.rreader.catalog.DownloadFailure
import com.giovannimartins.rreader.catalog.DownloadState
import com.giovannimartins.rreader.catalog.IRACEMA
import com.giovannimartins.rreader.catalog.SearchFailure
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a phone-sized screen; the list is lazy, so rows are scrolled into view before they are checked. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExploreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    @Test
    fun `lists the suggested classics, each with a large download button`() {
        show(ExploreUiState(curated = listOf(DOM_CASMURRO, IRACEMA)))

        compose.onNode(hasText("Explore") and isHeading()).assertExists()
        compose.onNodeWithText("Suggested classics").assertExists()
        scrollTo(hasContentDescription("Download Iracema"))
        compose.onNodeWithText("José de Alencar").assertExists()
        compose.onNodeWithText("A virgem dos lábios de mel.").assertExists()
        compose
            .onNodeWithContentDescription("Download Iracema")
            .assertHeightIsAtLeast(56.dp)
            .performClick()

        assertThat(events).containsExactly("download 67740")
    }

    @Test
    fun `says when the phone is offline`() {
        show(ExploreUiState(online = false, curated = listOf(DOM_CASMURRO)))

        compose
            .onNodeWithText("You are offline. Connect to Wi-Fi or mobile data to search and download books.")
            .assertExists()
    }

    @Test
    fun `searches for the words typed`() {
        show(ExploreUiState(curated = listOf(DOM_CASMURRO)))
        compose.onNodeWithText("Search").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("machado")
        compose
            .onNodeWithText("Search")
            .assertIsEnabled()
            .assertHeightIsAtLeast(56.dp)
            .performClick()

        assertThat(events).containsExactly("query machado", "search").inOrder()
    }

    @Test
    fun `follows a download until the book is in the library`() {
        show(
            ExploreUiState(
                curated = listOf(DOM_CASMURRO, IRACEMA),
                downloads =
                    mapOf(
                        DOM_CASMURRO.id to DownloadState.Downloading(0.45f),
                        IRACEMA.id to DownloadState.InLibrary("livro", alreadyThere = false),
                    ),
            ),
        )

        scrollTo(hasText("Downloading… 45%"))
        scrollTo(hasText("Added to your library."))
        compose
            .onNodeWithText("Open in the library")
            .assertHeightIsAtLeast(56.dp)
            .performClick()

        assertThat(events).containsExactly("library")
    }

    @Test
    fun `a book already in the library says so`() {
        show(
            ExploreUiState(
                curated = listOf(DOM_CASMURRO),
                downloads = mapOf(DOM_CASMURRO.id to DownloadState.InLibrary("livro", alreadyThere = true)),
            ),
        )

        scrollTo(hasText("This book is already in your library."))
    }

    @Test
    fun `a download waiting for the internet says so`() {
        show(
            ExploreUiState(curated = listOf(DOM_CASMURRO), downloads = mapOf(DOM_CASMURRO.id to DownloadState.Waiting)),
        )

        scrollTo(hasText("Waiting for the internet to download…"))
    }

    @Test
    fun `a failed download explains why and can be tried again`() {
        show(
            ExploreUiState(
                curated = listOf(DOM_CASMURRO),
                downloads = mapOf(DOM_CASMURRO.id to DownloadState.Failed(DownloadFailure.TOO_LARGE)),
            ),
        )

        scrollTo(hasText("This book is too large to download (limit: 100 MB)."))
        compose
            .onNodeWithContentDescription("Try downloading Dom Casmurro again")
            .assertHeightIsAtLeast(56.dp)
            .performClick()

        assertThat(events).containsExactly("download 55752")
    }

    @Test
    fun `shows search results, offers more and goes back to the suggestions`() {
        show(
            ExploreUiState(
                curated = listOf(IRACEMA),
                query = "machado",
                search = SearchUiState.Results("machado", listOf(DOM_CASMURRO), canLoadMore = true),
            ),
        )

        compose.onNode(hasText("Results for “machado”") and isHeading()).assertExists()
        compose.onNodeWithText("Iracema").assertDoesNotExist()
        scrollTo(hasText("More results"))
        compose.onNodeWithText("More results").assertHeightIsAtLeast(56.dp).performClick()
        scrollTo(hasText("Back to the suggestions"))
        compose.onNodeWithText("Back to the suggestions").performClick()

        assertThat(events).containsExactly("more", "clear").inOrder()
    }

    @Test
    fun `a search that found nothing says so`() {
        show(ExploreUiState(search = SearchUiState.Results("xyz", emptyList(), canLoadMore = false)))

        compose.onNodeWithText("No books found. Try another title or author.").assertExists()
        compose.onNodeWithText("More results").assertDoesNotExist()
    }

    @Test
    fun `a failed search explains why and can be tried again`() {
        show(ExploreUiState(search = SearchUiState.Failed("machado", SearchFailure.OFFLINE)))

        compose.onNodeWithText("No internet connection. Connect and try again.").assertExists()
        compose
            .onNodeWithText("Try again")
            .assertHeightIsAtLeast(56.dp)
            .performClick()

        assertThat(events).containsExactly("retry")
    }

    @Test
    fun `says when the free books site is down, for searches and downloads`() {
        show(ExploreUiState(search = SearchUiState.Failed("machado", SearchFailure.SITE_DOWN)))

        compose.onNodeWithText(SITE_DOWN).assertExists()
    }

    @Test
    fun `a download that failed because the site is down says so`() {
        show(
            ExploreUiState(
                curated = listOf(DOM_CASMURRO),
                downloads = mapOf(DOM_CASMURRO.id to DownloadState.Failed(DownloadFailure.SITE_DOWN)),
            ),
        )

        scrollTo(hasText(SITE_DOWN))
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        compose.onNode(matcher).assertExists()
    }

    private fun show(initial: ExploreUiState) {
        var state by mutableStateOf(initial)
        compose.setContent {
            RReaderTheme {
                ExploreScreen(
                    state = state,
                    onQueryChange = {
                        state = state.copy(query = it)
                        events += "query $it"
                    },
                    onSearch = { events += "search" },
                    onClearSearch = { events += "clear" },
                    onMoreResults = { events += "more" },
                    onRetrySearch = { events += "retry" },
                    onDownload = { events += "download ${it.id}" },
                    onOpenLibrary = { events += "library" },
                )
            }
        }
    }

    private companion object {
        const val SITE_DOWN = "The free books site is under maintenance. Try again later."
    }
}
