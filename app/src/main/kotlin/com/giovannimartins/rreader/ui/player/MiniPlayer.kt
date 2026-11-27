package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.components.BookCover
import com.giovannimartins.rreader.ui.theme.RReaderTheme

/** The open book above the navigation bar: tap to open the player, or control it from here. */
@Composable
fun MiniPlayer(
    nowPlaying: NowPlaying,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = nowPlaying.title.ifBlank { stringResource(R.string.library_untitled) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(progress = { nowPlaying.progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClickLabel = stringResource(R.string.player_open), onClick = onOpen),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(title = title, coverFile = nowPlaying.coverFile, modifier = Modifier.width(44.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = nowPlaying.chapterTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PlayerIconButton(
                    icon = R.drawable.ic_replay_15,
                    label = R.string.player_back_15,
                    onClick = onSeekBack,
                    size = 56.dp,
                )
                PlayPauseButton(nowPlaying = nowPlaying, onClick = onTogglePlayPause, size = 64.dp)
            }
        }
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    RReaderTheme {
        MiniPlayer(nowPlaying = previewNowPlaying(), onOpen = {}, onTogglePlayPause = {}, onSeekBack = {})
    }
}

/** Sample state for previews. */
internal fun previewNowPlaying(): NowPlaying =
    NowPlaying(
        bookId = "00000000-0000-0000-0000-000000000001",
        title = "Dom Casmurro",
        authors = "Machado de Assis",
        coverFile = null,
        chapterIndex = 2,
        chapterTitle = "Capítulo III — A denúncia",
        chapterCount = 3,
        chapterTitles = listOf("Capítulo I — Do título", "Capítulo II — Do livro", "Capítulo III — A denúncia"),
        isPlaying = true,
        isBuffering = false,
        isEnded = false,
        positionMs = 95_000,
        durationMs = 412_000,
        bookRemainingMs = 12_317_000,
        speed = 1.25f,
        problem = null,
    )
