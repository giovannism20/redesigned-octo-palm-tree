package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.ui.components.BookCover
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.components.timeLeft
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Stateful full player bound to [PlayerViewModel]; shows nothing while no book is open. */
@Composable
fun PlayerRoute(
    onMinimize: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val current = nowPlaying ?: return
    PlayerScreen(
        nowPlaying = current,
        sleepTimer = sleepTimer,
        onMinimize = onMinimize,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeekBack = viewModel::seekBack,
        onSeekForward = viewModel::seekForward,
        onSeekTo = viewModel::seekTo,
        onSelectChapter = viewModel::playChapter,
        onSpeedChange = viewModel::setSpeed,
        onSetSleepTimer = viewModel::setSleepTimer,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
        onRetry = viewModel::retry,
        onOpenVoices = onOpenVoices,
        onOpenReader = onOpenReader,
        modifier = modifier,
    )
}

/** The full player: cover, titles, chapter progress, large controls, speed, sleep timer and chapters. */
@Composable
fun PlayerScreen(
    nowPlaying: NowPlaying,
    sleepTimer: SleepTimerState,
    onMinimize: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (SleepTimerSetting) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onRetry: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChapters by rememberSaveable { mutableStateOf(false) }
    val title = nowPlaying.title.ifBlank { stringResource(R.string.library_untitled) }
    ScreenSurface(modifier = modifier) {
        // Fills exactly one screen while everything fits (the cover takes the space left over) and scrolls only
        // when the system text is very large, so no control is ever cut off below the screen edge.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerTopBar(onMinimize = onMinimize)
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    BookCover(
                        title = title,
                        coverFile = nowPlaying.coverFile,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
                BookHeading(nowPlaying = nowPlaying, title = title)
                ChapterProgress(nowPlaying = nowPlaying, onSeekTo = onSeekTo)
                TimeLeftLines(nowPlaying = nowPlaying)
                TransportControls(
                    nowPlaying = nowPlaying,
                    onSeekBack = onSeekBack,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekForward = onSeekForward,
                )
                PlayerOptions(
                    speed = nowPlaying.speed,
                    sleepTimer = sleepTimer,
                    onSpeedChange = onSpeedChange,
                    onSetSleepTimer = onSetSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onShowChapters = { showChapters = true },
                    onOpenReader = onOpenReader,
                )
                PlayerStatus(nowPlaying = nowPlaying, onRetry = onRetry, onOpenVoices = onOpenVoices)
            }
        }
        if (showChapters) {
            ChapterSheet(
                nowPlaying = nowPlaying,
                onSelect = { index ->
                    showChapters = false
                    onSelectChapter(index)
                },
                onDismiss = { showChapters = false },
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlayerIconButton(
            icon = R.drawable.ic_minimize,
            label = R.string.player_minimize,
            onClick = onMinimize,
            size = 56.dp,
        )
        Text(
            text = stringResource(R.string.player_now_playing),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).semantics { heading() },
        )
    }
}

@Composable
private fun BookHeading(
    nowPlaying: NowPlaying,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (nowPlaying.authors.isNotBlank()) {
            Text(
                text = nowPlaying.authors,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = nowPlaying.chapterTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (nowPlaying.chapterCount > 1) {
            Text(
                text = stringResource(R.string.player_chapter_of, nowPlaying.chapterIndex + 1, nowPlaying.chapterCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The chapter seek bar with elapsed and remaining time. Dragging previews the time; releasing seeks. */
@Composable
private fun ChapterProgress(
    nowPlaying: NowPlaying,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slider = rememberSliderState(value = nowPlaying.progress)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlaying.progress, dragging) {
        if (!dragging) slider.value = nowPlaying.progress
    }
    val shownMs = if (dragging) (slider.value * nowPlaying.durationMs).toLong() else nowPlaying.positionMs
    val elapsed = PlayerFormat.duration(shownMs)
    val left = PlayerFormat.duration(nowPlaying.durationMs - shownMs)
    val positionLabel = stringResource(R.string.player_position)
    val positionState =
        stringResource(R.string.player_position_state, elapsed, PlayerFormat.duration(nowPlaying.durationMs))
    val leftLabel = stringResource(R.string.player_time_left, left)
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            state = slider,
            onValueChange = { value ->
                dragging = true
                slider.value = value
            },
            onValueChangeFinished = {
                dragging = false
                onSeekTo((slider.value * nowPlaying.durationMs).toLong())
            },
            enabled = nowPlaying.durationMs > 0,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = positionLabel
                    stateDescription = positionState
                },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = elapsed, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.player_time_left_short, left),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clearAndSetSemantics { contentDescription = leftLabel },
            )
        }
    }
}

/** How long listening to the rest of the chapter and of the book takes at the current speed. */
@Composable
private fun TimeLeftLines(
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val lines =
        listOfNotNull(
            resources.timeLeft(R.plurals.player_chapter_time_left, nowPlaying.chapterTimeLeft),
            resources.timeLeft(R.plurals.player_book_time_left, nowPlaying.bookTimeLeft),
        )
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TransportControls(
    nowPlaying: NowPlaying,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(
            icon = R.drawable.ic_replay_15,
            label = R.string.player_back_15,
            onClick = onSeekBack,
            size = 72.dp,
        )
        PlayPauseButton(nowPlaying = nowPlaying, onClick = onTogglePlayPause, size = 96.dp)
        PlayerIconButton(
            icon = R.drawable.ic_forward_15,
            label = R.string.player_forward_15,
            onClick = onSeekForward,
            size = 72.dp,
        )
    }
}

/** Buffering, end of the book or a problem with what to do next. */
@Composable
private fun PlayerStatus(
    nowPlaying: NowPlaying,
    onRetry: () -> Unit,
    onOpenVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val problem = nowPlaying.problem
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            problem != null -> {
                ProblemCard(problem = problem, onRetry = onRetry, onOpenVoices = onOpenVoices)
            }

            nowPlaying.isEnded -> {
                Text(
                    text = stringResource(R.string.player_finished),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            nowPlaying.isPlaying && nowPlaying.isBuffering -> {
                Text(
                    text = stringResource(R.string.player_preparing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A problem and what to do next: install a voice, or try again. */
@Composable
private fun ProblemCard(
    problem: PlaybackProblem,
    onRetry: () -> Unit,
    onOpenVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noVoice = problem == PlaybackProblem.NO_VOICE
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(problem.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = if (noVoice) onOpenVoices else onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    text = stringResource(if (noVoice) R.string.player_open_voices else R.string.player_retry),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PlayerScreenPreview() {
    RReaderTheme {
        PlayerScreen(
            nowPlaying = previewNowPlaying(),
            sleepTimer = SleepTimerState.After(29.minutes + 59.seconds),
            onMinimize = {},
            onTogglePlayPause = {},
            onSeekBack = {},
            onSeekForward = {},
            onSeekTo = {},
            onSelectChapter = {},
            onSpeedChange = {},
            onSetSleepTimer = {},
            onCancelSleepTimer = {},
            onRetry = {},
            onOpenVoices = {},
            onOpenReader = {},
        )
    }
}

@Preview
@Composable
private fun PlayerScreenNoVoicePreview() {
    RReaderTheme {
        PlayerScreen(
            nowPlaying = previewNowPlaying().copy(isPlaying = false, problem = PlaybackProblem.NO_VOICE),
            sleepTimer = SleepTimerState.Off,
            onMinimize = {},
            onTogglePlayPause = {},
            onSeekBack = {},
            onSeekForward = {},
            onSeekTo = {},
            onSelectChapter = {},
            onSpeedChange = {},
            onSetSleepTimer = {},
            onCancelSleepTimer = {},
            onRetry = {},
            onOpenVoices = {},
            onOpenReader = {},
        )
    }
}
