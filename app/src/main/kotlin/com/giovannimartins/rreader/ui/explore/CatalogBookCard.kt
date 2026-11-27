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

/** One book: author, title, description when there is one, and its download button or progress. */
@Composable
internal fun CatalogBookCard(
    entry: CatalogEntry,
    download: DownloadState?,
    onDownload: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = entry.title, style = MaterialTheme.typography.titleMedium)
            entry.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            DownloadAction(
                title = entry.title,
                state = download,
                onDownload = onDownload,
                onOpenLibrary = onOpenLibrary,
            )
        }
    }
}

/** The download button, or how the download goes and what to do next. */
@Composable
private fun DownloadAction(
    title: String,
    state: DownloadState?,
    onDownload: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            null -> {
                DownloadButton(title = title, retry = false, onClick = onDownload)
            }

            DownloadState.Waiting -> {
                Status(stringResource(R.string.explore_waiting))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is DownloadState.Downloading -> {
                val fraction = state.fraction
                if (fraction == null) {
                    Status(stringResource(R.string.explore_downloading))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Status(stringResource(R.string.explore_downloading_percent, (fraction * PERCENT).roundToInt()))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            is DownloadState.InLibrary -> {
                val done = if (state.alreadyThere) R.string.explore_already_in_library else R.string.explore_added
                Status(stringResource(done), color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(
                        text = stringResource(R.string.explore_open_library),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            is DownloadState.Failed -> {
                Status(stringResource(state.reason.messageRes()), color = MaterialTheme.colorScheme.error)
                DownloadButton(title = title, retry = true, onClick = onDownload)
            }
        }
    }
}

@Composable
private fun DownloadButton(
    title: String,
    retry: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(if (retry) R.string.explore_retry_download_book else R.string.explore_download_book, title)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_download), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (retry) R.string.explore_try_again else R.string.explore_download),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A short status line that TalkBack reads out when it changes. */
@Composable
private fun Status(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private const val PERCENT = 100
