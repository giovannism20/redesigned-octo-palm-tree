package com.giovannimartins.rreader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.ui.components.BookCover
import com.giovannimartins.rreader.ui.importer.messageRes
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import kotlin.math.roundToInt

/** Stateful library screen bound to [LibraryViewModel]. */
@Composable
fun LibraryRoute(
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onAddBook = onAddBook, onRemove = viewModel::remove, modifier = modifier)
}

/** The library: "Continue listening" first, then every book with its progress. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAddBook: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    when (state) {
        LibraryUiState.Loading -> {
            Column(
                modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        is LibraryUiState.Content -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (state.books.isEmpty()) {
                    item { LibraryEmptyState(onAddBook = onAddBook) }
                }
                state.continueListening?.let { book ->
                    item { ContinueListeningCard(book) }
                }
                items(state.books, key = { it.id }) { book ->
                    LibraryBookRow(book = book, onRemove = { pendingRemoval = book.id })
                }
            }
        }
    }
    pendingRemoval?.let { bookId ->
        RemoveBookDialog(
            onConfirm = {
                onRemove(bookId)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun LibraryEmptyState(
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddBook, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(text = stringResource(R.string.import_title), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ContinueListeningCard(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(title = book.displayTitle(), coverFile = book.coverFile, modifier = Modifier.width(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.library_continue),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = book.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressLine(book)
            }
        }
    }
}

@Composable
private fun LibraryBookRow(
    book: LibraryBook,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BookCover(title = book.displayTitle(), coverFile = book.coverFile, modifier = Modifier.width(88.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (book.authors.isNotEmpty()) {
                Text(
                    text = book.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = book.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (book.status) {
                BookStatus.IMPORTING -> {
                    Text(text = stringResource(R.string.library_preparing), style = MaterialTheme.typography.bodyMedium)
                }

                BookStatus.READY -> {
                    ProgressLine(book)
                }

                BookStatus.FAILED -> {
                    Text(
                        text = stringResource((book.failure ?: ImportFailure.CORRUPTED).messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = stringResource(R.string.library_remove), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProgressLine(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth())
        val label =
            if (book.started) {
                stringResource(R.string.library_progress, (book.progress * PERCENT).roundToInt())
            } else {
                stringResource(R.string.library_not_started)
            }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoveBookDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_remove_title)) },
        text = { Text(stringResource(R.string.library_remove_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LibraryBook.displayTitle(): String = title.ifBlank { stringResource(R.string.library_untitled) }

private const val PERCENT = 100

@Preview
@Composable
private fun LibraryScreenPreview() {
    val book =
        LibraryBook(
            id = "1",
            title = "Dom Casmurro",
            authors = listOf("Machado de Assis"),
            status = BookStatus.READY,
            failure = null,
            coverFile = null,
            charsRead = 45,
            totalChars = 100,
        )
    RReaderTheme {
        LibraryScreen(
            state = LibraryUiState.Content(continueListening = book, books = listOf(book)),
            onAddBook = {},
            onRemove = {},
        )
    }
}
