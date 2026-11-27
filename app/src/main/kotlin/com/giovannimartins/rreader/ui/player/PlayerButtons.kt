package com.giovannimartins.rreader.ui.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R

private const val ICON_TO_BUTTON = 0.5f

/** The main button: pause while listening, play otherwise, with a spinner while the voice prepares audio. */
@Composable
internal fun PlayPauseButton(
    nowPlaying: NowPlaying,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (nowPlaying.isPlaying) R.string.player_pause else R.string.player_play)
    FilledIconButton(onClick = onClick, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = description,
                modifier = Modifier.size(size * ICON_TO_BUTTON),
            )
            if (nowPlaying.isPlaying && nowPlaying.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size - 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

/** An icon-only button with a spoken label, such as −15 s. */
@Composable
internal fun PlayerIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(size)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(size * ICON_TO_BUTTON),
        )
    }
}
