package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.playback.SleepTimerState

/** Speed, sleep timer, chapters and the follow-along text, wrapping onto more lines when the text is large. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerOptions(
    speed: Float,
    sleepTimer: SleepTimerState,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (SleepTimerSetting) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onShowChapters: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpeedButton(speed = speed, onSpeedChange = onSpeedChange)
        SleepTimerButton(state = sleepTimer, onSet = onSetSleepTimer, onCancel = onCancelSleepTimer)
        OutlinedButton(onClick = onShowChapters, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(painterResource(R.drawable.ic_chapters), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_chapters), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(onClick = onOpenReader, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(painterResource(R.drawable.ic_reader), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_text), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SpeedButton(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val value = PlayerFormat.speed(speed, locale)
    val description = stringResource(R.string.player_speed_description, value)
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
        ) {
            Icon(painterResource(R.drawable.ic_speed), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_speed_value, value), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlayerFormat.SPEEDS.forEach { option ->
                val optionText = PlayerFormat.speed(option, locale)
                val optionDescription = stringResource(R.string.player_speed_description, optionText)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.player_speed_value, optionText),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (option == speed) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSpeedChange(option)
                    },
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = optionDescription },
                )
            }
        }
    }
}
