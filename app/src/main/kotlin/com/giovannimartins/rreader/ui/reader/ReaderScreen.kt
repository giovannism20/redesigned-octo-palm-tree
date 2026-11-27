package com.giovannimartins.rreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.player.NowPlaying
import com.giovannimartins.rreader.ui.player.PlayPauseButton
import com.giovannimartins.rreader.ui.player.PlayerIconButton
import com.giovannimartins.rreader.ui.player.PlayerViewModel
import com.giovannimartins.rreader.ui.player.previewNowPlaying
import com.giovannimartins.rreader.ui.theme.RReaderTheme

/** Stateful follow-along reader; it starts at the reading each time it opens, and shows nothing without a book. */
@Composable
fun ReaderRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.backToReading() }
    val current = nowPlaying ?: return
    ReaderScreen(
        state = state,
        nowPlaying = current,
        onClose = onClose,
        onShrinkText = viewModel::shrinkText,
        onEnlargeText = viewModel::enlargeText,
        onUserScroll = viewModel::onUserScroll,
        onBackToReading = viewModel::backToReading,
        onReadFrom = viewModel::readFrom,
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onSeekBack = playerViewModel::seekBack,
        onSeekForward = playerViewModel::seekForward,
        modifier = modifier,
    )
}

/**
 * The follow-along reader: the chapter being read in large, adjustable type with the sentence being spoken
 * highlighted and kept in view, "Back to the reading" after scrolling away, and the main playback controls.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    nowPlaying: NowPlaying,
    onClose: () -> Unit,
    onShrinkText: () -> Unit,
    onEnlargeText: () -> Unit,
    onUserScroll: () -> Unit,
    onBackToReading: () -> Unit,
    onReadFrom: (paragraph: Int, offset: Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ReaderTopBar(
                title = state.chapterTitle,
                canShrinkText = state.canShrinkText,
                canEnlargeText = state.canEnlargeText,
                onClose = onClose,
                onShrinkText = onShrinkText,
                onEnlargeText = onEnlargeText,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ReaderBody(state = state, onUserScroll = onUserScroll, onReadFrom = onReadFrom)
                if (!state.following && state.paragraphs != null) {
                    Button(
                        onClick = onBackToReading,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reader_back_to_reading),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(R.drawable.ic_replay_15, R.string.player_back_15, onClick = onSeekBack, size = 56.dp)
                PlayPauseButton(nowPlaying = nowPlaying, onClick = onTogglePlayPause, size = 72.dp)
                PlayerIconButton(
                    R.drawable.ic_forward_15,
                    R.string.player_forward_15,
                    onClick = onSeekForward,
                    size = 56.dp,
                )
            }
        }
    }
}

/** The text, or what happens instead: the chapter is opening, or it could not be read. */
@Composable
private fun ReaderBody(
    state: ReaderUiState,
    onUserScroll: () -> Unit,
    onReadFrom: (paragraph: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paragraphs = state.paragraphs
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            state.failed -> {
                Text(
                    text = stringResource(R.string.reader_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }

            paragraphs == null -> {
                val loading = stringResource(R.string.reader_loading)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
            }

            else -> {
                ReaderText(
                    chapterIndex = state.chapterIndex,
                    paragraphs = paragraphs,
                    highlight = state.highlight,
                    following = state.following,
                    followRequests = state.followRequests,
                    textScale = state.textScale,
                    onUserScroll = onUserScroll,
                    onReadFrom = onReadFrom,
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    canShrinkText: Boolean,
    canEnlargeText: Boolean,
    onClose: () -> Unit,
    onShrinkText: () -> Unit,
    onEnlargeText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(R.drawable.ic_arrow_back, R.string.reader_close, onClick = onClose, size = 56.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
        )
        TextSizeButton(
            symbol = stringResource(R.string.reader_text_smaller_symbol),
            label = stringResource(R.string.reader_text_smaller),
            enabled = canShrinkText,
            onClick = onShrinkText,
        )
        TextSizeButton(
            symbol = stringResource(R.string.reader_text_larger_symbol),
            label = stringResource(R.string.reader_text_larger),
            enabled = canEnlargeText,
            onClick = onEnlargeText,
        )
    }
}

/** "A−" or "A+", spoken as what it does. */
@Composable
private fun TextSizeButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(56.dp).semantics { contentDescription = label },
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Preview
@Composable
private fun ReaderScreenPreview() {
    RReaderTheme {
        ReaderScreen(
            state =
                ReaderUiState(
                    chapterIndex = 2,
                    chapterTitle = "Capítulo III — A denúncia",
                    paragraphs =
                        listOf(
                            "Ia entrar na sala de visitas, quando ouvi proferir o meu nome.",
                            "A casa era a da rua de Mata-cavalos, o mês novembro, o ano é que é um tanto remoto.",
                        ),
                    highlight = ReaderHighlight(paragraph = 0, start = 0, end = 92),
                    following = false,
                ),
            nowPlaying = previewNowPlaying(),
            onClose = {},
            onShrinkText = {},
            onEnlargeText = {},
            onUserScroll = {},
            onBackToReading = {},
            onReadFrom = { _, _ -> },
            onTogglePlayPause = {},
            onSeekBack = {},
            onSeekForward = {},
        )
    }
}
