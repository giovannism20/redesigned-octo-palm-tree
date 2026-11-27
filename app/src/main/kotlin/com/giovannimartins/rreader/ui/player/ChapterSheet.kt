package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.components.AppModalBottomSheet

/** The book's chapters; the current one is marked and choosing one starts it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterSheet(
    nowPlaying: NowPlaying,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Text(
            text = stringResource(R.string.player_chapters),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = nowPlaying.chapterIndex)) {
            itemsIndexed(nowPlaying.chapterTitles) { index, title ->
                ChapterRow(
                    title = title,
                    current = index == nowPlaying.chapterIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    title: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { selected = current }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current) FontWeight.Bold else null,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (current) {
            Text(
                text = stringResource(R.string.player_chapter_current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
