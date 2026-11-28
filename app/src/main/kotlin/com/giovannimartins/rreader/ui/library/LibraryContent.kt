package com.giovannimartins.rreader.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.ui.components.BookCover
import kotlin.time.Duration

/**
 * The library's books: the title and "Add a book", then the books being listened to ("Listening"), then every other
 * book ("In your library"), or what to do when there is none. [onRemove] asks to remove a book.
 */
@Composable
internal fun LibraryContent(
    state: LibraryUiState.Content,
    onAddBook: () -> Unit,
    onPlay: (String) -> Unit,
    onPause: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        item { AddBookButton(onClick = onAddBook) }
        if (state.listening.isEmpty() && state.shelf.isEmpty()) {
            item { LibraryEmptyState() }
        }
        if (state.listening.isNotEmpty()) {
            item { SectionHeading(R.string.library_listening) }
        }
        items(state.listening, key = { it.id }) { book ->
            ListeningCard(
                book = book,
                timeLeft = state.timeLeft[book.id],
                playing = state.playingBookId == book.id,
                onPlay = { onPlay(book.id) },
                onPause = onPause,
                onRemove = { onRemove(book.id) },
            )
        }
        if (state.shelf.isNotEmpty()) {
            item { SectionHeading(R.string.library_shelf) }
        }
        items(state.shelf, key = { it.id }) { book ->
            LibraryBookRow(
                book = book,
                timeLeft = state.timeLeft[book.id],
                onPlay = { onPlay(book.id) }.takeIf { book.status == BookStatus.READY },
                onRemove = { onRemove(book.id) },
            )
        }
    }
}

/** The heading of a part of the library: the books being listened to, or the others. */
@Composable
private fun SectionHeading(
    @StringRes text: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/**
 * A book the listener is in the middle of: tap it to listen and open the player. Its button pauses it while it
 * plays, as the mini player's does, and listens otherwise.
 */
@Composable
private fun ListeningCard(
    book: LibraryBook,
    timeLeft: Duration?,
    playing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onPlay, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(title = book.displayTitle(), coverFile = book.coverFile, modifier = Modifier.width(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = book.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressLine(book = book, timeLeft = timeLeft)
                Button(onClick = if (playing) onPause else onPlay, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (playing) R.string.player_pause else R.string.library_listen),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(text = stringResource(R.string.library_remove), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
