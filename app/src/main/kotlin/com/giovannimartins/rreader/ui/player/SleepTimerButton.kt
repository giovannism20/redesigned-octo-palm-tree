package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState
import kotlin.time.Duration.Companion.minutes

/**
 * The sleep timer in the player: "Sleep timer" while it is off, the countdown while it runs, or "End of
 * chapter". Its menu offers the durations, the end of the chapter and, while it is on, turning it off.
 */
@Composable
internal fun SleepTimerButton(
    state: SleepTimerState,
    onSet: (SleepTimerSetting) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label =
        when (state) {
            SleepTimerState.Off -> stringResource(R.string.player_sleep_timer)
            is SleepTimerState.After -> PlayerFormat.duration(state.remaining.inWholeMilliseconds)
            SleepTimerState.EndOfChapter -> stringResource(R.string.player_sleep_end_of_chapter)
        }
    val description =
        when (state) {
            SleepTimerState.Off -> label
            is SleepTimerState.After -> stringResource(R.string.player_sleep_timer_running, label)
            SleepTimerState.EndOfChapter -> stringResource(R.string.player_sleep_timer_chapter)
        }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
        ) {
            Icon(painterResource(R.drawable.ic_sleep_timer), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(R.string.player_sleep_timer_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            PlayerFormat.SLEEP_MINUTES.forEach { minutes ->
                SleepTimerOption(text = minutesLabel(minutes), current = false) {
                    expanded = false
                    onSet(SleepTimerSetting.After(minutes.minutes))
                }
            }
            SleepTimerOption(
                text = stringResource(R.string.player_sleep_end_of_chapter),
                current = state == SleepTimerState.EndOfChapter,
            ) {
                expanded = false
                onSet(SleepTimerSetting.EndOfChapter)
            }
            if (state != SleepTimerState.Off) {
                SleepTimerOption(text = stringResource(R.string.player_sleep_cancel), current = false) {
                    expanded = false
                    onCancel()
                }
            }
        }
    }
}

@Composable
private fun SleepTimerOption(
    text: String,
    current: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

/** "5 minutes", …, "1 hour". */
@Composable
private fun minutesLabel(minutes: Int): String =
    if (minutes == MINUTES_PER_HOUR) {
        stringResource(R.string.player_sleep_hour)
    } else {
        pluralStringResource(R.plurals.player_sleep_minutes, minutes, minutes)
    }

private const val MINUTES_PER_HOUR = 60
