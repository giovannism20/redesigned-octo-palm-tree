package com.giovannimartins.rreader.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
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
import com.giovannimartins.rreader.importer.ImportProgress
import com.giovannimartins.rreader.ui.components.AppAlertDialog
import com.giovannimartins.rreader.ui.components.BookCover
import com.giovannimartins.rreader.ui.components.timeLeft
import com.giovannimartins.rreader.ui.importer.messageRes
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Stateful library screen bound to [LibraryViewModel]; starting a book opens the player. */
@Composable
fun LibraryRoute(
    onAddBook: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onAddBook = onAddBook,
        onPlay = { bookId ->
            viewModel.play(bookId)
            onOpenPlayer()
        },
        onPause = viewModel::pause,
        onRemove = viewModel::remove,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * The library: a large "Add a book" action, then the books being listened to ("Listening") and every other
 * book ("In your library"), each with its progress. Tap a ready book to listen. When the library cannot be
 * read, it says so and offers to try again ([onRetry]).
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAddBook: () -> Unit,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: () -> Unit = {},
) {
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    when (state) {
        LibraryUiState.Loading -> {
            LibraryLoading(modifier = modifier.fillMaxSize())
        }

        LibraryUiState.Unavailable -> {
            LibraryUnavailable(onRetry = onRetry, modifier = modifier.fillMaxSize())
        }

        is LibraryUiState.Content -> {
            LibraryContent(
                state = state,
                onAddBook = onAddBook,
                onPlay = onPlay,
                onPause = onPause,
                onRemove = { pendingRemoval = it },
                modifier = modifier.fillMaxSize(),
            )
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

/** The large action at the top of the library: it opens the page that adds a book from a file. */
@Composable
internal fun AddBookButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Icon(painterResource(R.drawable.ic_add_book), contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text = stringResource(R.string.library_add_book), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
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
    }
}

@Composable
internal fun LibraryBookRow(
    book: LibraryBook,
    timeLeft: Duration?,
    onPlay: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listenLabel = stringResource(R.string.library_listen)
    val playable =
        if (onPlay == null) Modifier else Modifier.clickable(onClickLabel = listenLabel, onClick = onPlay)
    Row(modifier = modifier.fillMaxWidth().then(playable), verticalAlignment = Alignment.Top) {
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
                    PreparingLine(book.importProgress)
                }

                BookStatus.READY -> {
                    ProgressLine(book = book, timeLeft = timeLeft)
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

/** The book's progress, and how long listening to the rest takes at the listener's speed. */
@Composable
internal fun ProgressLine(
    book: LibraryBook,
    timeLeft: Duration?,
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
        val left = timeLeft?.let { LocalResources.current.timeLeft(R.plurals.library_time_left, it) }
        if (left != null) {
            Text(
                text = left,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Preparing the book…", or the page text recognition is reading, with a progress bar. */
@Composable
private fun PreparingLine(
    progress: ImportProgress?,
    modifier: Modifier = Modifier,
) {
    when (progress) {
        is ImportProgress.RecognizingText -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress.page.toFloat() / progress.pageCount },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.import_recognizing_text, progress.page, progress.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        null -> {
            Text(
                text = stringResource(R.string.library_preparing),
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RemoveBookDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_remove_title)) },
        text = { Text(stringResource(R.string.library_remove_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun LibraryBook.displayTitle(): String = title.ifBlank { stringResource(R.string.library_untitled) }

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
            state =
                LibraryUiState.Content(
                    listening = listOf(book),
                    shelf = emptyList(),
                    timeLeft = mapOf(book.id to 3.hours + 20.minutes),
                ),
            onAddBook = {},
            onPlay = {},
            onRemove = {},
            onRetry = {},
        )
    }
}
