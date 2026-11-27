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

@Composable
internal fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val search = {
        keyboard?.hide()
        onSearch()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.explore_search_label)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = search,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(text = stringResource(R.string.explore_search), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun Searching(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Spacer(Modifier.width(16.dp))
        Text(text = stringResource(R.string.explore_searching), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SearchProblem(
    search: SearchUiState.Failed,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(search.failure.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(text = stringResource(R.string.explore_try_again), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onClearSearch, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = stringResource(R.string.explore_back_to_suggestions),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun ResultsHeader(
    search: SearchUiState.Results,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.explore_results_title, search.terms),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (search.entries.isEmpty()) {
            Text(text = stringResource(R.string.explore_no_results), style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onClearSearch, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = stringResource(R.string.explore_back_to_suggestions),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun MoreResults(
    search: SearchUiState.Results,
    onMoreResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        search.moreFailure?.let { failure ->
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            search.loadingMore -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            search.canLoadMore -> {
                OutlinedButton(onClick = onMoreResults, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(
                        text = stringResource(R.string.explore_more_results),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
