package com.giovannimartins.rreader.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.catalog.CatalogEntry
import com.giovannimartins.rreader.catalog.DownloadFailure
import com.giovannimartins.rreader.catalog.DownloadState
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import kotlin.math.roundToInt

/** Stateful Explore screen bound to [ExploreViewModel]; "Open in the library" calls [onOpenLibrary]. */
@Composable
fun ExploreRoute(
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExploreScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onClearSearch = viewModel::clearSearch,
        onMoreResults = viewModel::moreResults,
        onRetrySearch = viewModel::retrySearch,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onOpenLibrary = onOpenLibrary,
        modifier = modifier,
    )
}

/**
 * The free catalogue: a search box over Project Gutenberg's books in Portuguese, and the curated classics while there
 * is no search. Every book has one large button that downloads it into the library, then follows the download.
 */
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onMoreResults: () -> Unit,
    onRetrySearch: () -> Unit,
    onDownload: (CatalogEntry) -> Unit,
    onCancelDownload: (CatalogEntry) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books = BookActions(state.downloads, onDownload, onCancelDownload, onOpenLibrary)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") { ExploreHeader(online = state.online) }
        item(key = "search") { SearchBox(query = state.query, onQueryChange = onQueryChange, onSearch = onSearch) }
        when (val search = state.search) {
            SearchUiState.Idle -> suggestions(state.curated, books)
            is SearchUiState.Loading -> item(key = "searching") { Searching() }
            is SearchUiState.Failed -> item(key = "failed") { SearchProblem(search, onRetrySearch, onClearSearch) }
            is SearchUiState.Results -> results(search, books, onMoreResults, onClearSearch)
        }
    }
}

/** What a book's row needs to show and start its download. */
private class BookActions(
    val downloads: Map<Int, DownloadState>,
    val onDownload: (CatalogEntry) -> Unit,
    val onCancel: (CatalogEntry) -> Unit,
    val onOpenLibrary: () -> Unit,
)

private fun LazyListScope.suggestions(
    curated: List<CatalogEntry>,
    books: BookActions,
) {
    item(key = "suggestions") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.explore_suggestions_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.explore_old_spelling),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    bookItems(curated, books)
}

private fun LazyListScope.results(
    search: SearchUiState.Results,
    books: BookActions,
    onMoreResults: () -> Unit,
    onClearSearch: () -> Unit,
) {
    item(key = "results") { ResultsHeader(search, onClearSearch) }
    bookItems(search.entries, books)
    item(key = "more") { MoreResults(search, onMoreResults) }
}

private fun LazyListScope.bookItems(
    entries: List<CatalogEntry>,
    books: BookActions,
) {
    items(entries, key = { "book-${it.id}" }) { entry ->
        CatalogBookCard(
            entry = entry,
            download = books.downloads[entry.id],
            onDownload = { books.onDownload(entry) },
            onCancel = { books.onCancel(entry) },
            onOpenLibrary = books.onOpenLibrary,
        )
    }
}

@Composable
private fun ExploreHeader(
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.explore_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(R.string.explore_intro), style = MaterialTheme.typography.bodyLarge)
        if (!online) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    text = stringResource(R.string.explore_offline),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ExploreScreenPreview() {
    val dom = CatalogEntry(55752, "Dom Casmurro", "Machado de Assis", "Bento Santiago relembra a juventude.")
    val iracema = CatalogEntry(67740, "Iracema", "José de Alencar", "A lenda da virgem dos lábios de mel.")
    val cortico = CatalogEntry(69187, "O Cortiço", "Aluísio Azevedo", "A vida agitada de um cortiço.")
    RReaderTheme {
        ExploreScreen(
            state =
                ExploreUiState(
                    online = false,
                    curated = listOf(dom, iracema, cortico),
                    downloads =
                        mapOf(
                            dom.id to DownloadState.Downloading(0.4f),
                            iracema.id to DownloadState.InLibrary("1", alreadyThere = false),
                            cortico.id to DownloadState.Failed(DownloadFailure.NETWORK),
                        ),
                ),
            onQueryChange = {},
            onSearch = {},
            onClearSearch = {},
            onMoreResults = {},
            onRetrySearch = {},
            onDownload = {},
            onCancelDownload = {},
            onOpenLibrary = {},
        )
    }
}
