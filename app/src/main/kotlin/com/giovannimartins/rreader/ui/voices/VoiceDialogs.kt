package com.giovannimartins.rreader.ui.voices

import android.text.format.Formatter
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import com.giovannimartins.rreader.tts.neural.NeuralVoices

@Composable
internal fun MobileDataDialog(
    voice: NeuralVoice,
    onAnswer: (Boolean?) -> Unit,
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, voice.pkg.archiveBytes)
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text(stringResource(R.string.voices_mobile_title)) },
        text = { Text(stringResource(R.string.voices_mobile_body, size), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_mobile_now), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_mobile_wait), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
internal fun DeleteDialog(
    voice: NeuralVoice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, voice.pkg.installedBytes)
    val others = NeuralVoices.voicesOf(voice.pkg).filter { it != voice }.map(NeuralVoice::name)
    val body =
        if (others.isEmpty()) {
            stringResource(R.string.voices_delete_body, size)
        } else {
            stringResource(R.string.voices_delete_body_shared, size, joinedNames(others))
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voices_delete_title, voice.name)) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_delete), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
