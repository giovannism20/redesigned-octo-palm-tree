package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.ui.components.BookCover

/**
 * The cover in the room the rest of the player leaves: it shrinks with very large text, and gives way when no room is
 * left, since drawn at its own size in no room it would cover the book's title.
 */
@Composable
internal fun PlayerCover(
    title: String,
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (maxHeight >= MIN_COVER_HEIGHT) {
            BookCover(title = title, coverFile = nowPlaying.coverFile, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

/** Below this height (a cover of 32 × 48 dp) the cover shows nothing useful, so it gives way. */
private val MIN_COVER_HEIGHT = 48.dp
