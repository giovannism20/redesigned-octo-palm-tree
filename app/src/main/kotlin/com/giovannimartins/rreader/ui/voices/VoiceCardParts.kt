package com.giovannimartins.rreader.ui.voices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.neural.VoiceDownloadFailure

/** A voice's card: raised, or, for the voice in use, the selection colour with a border. */
@Composable
internal fun VoiceCard(
    inUse: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors =
        if (inUse) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    val border = if (inUse) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun VoiceHeading(
    look: VoiceLook,
    name: String,
    inUse: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceEmblem(look = look)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(look.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor(inUse),
                )
            }
        }
        if (inUse) InUseLabel()
    }
}

/** "In use", with a check mark: the voice in use is never told by colour alone. */
@Composable
internal fun InUseLabel(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.voices_in_use),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun SampleButton(
    name: String,
    sampling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (sampling) R.string.voices_stop_sample else R.string.voices_listen_to, name)
    val playing = stringResource(R.string.voices_stop)
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier.heightIn(min = 56.dp).semantics {
                contentDescription = description
                if (sampling) stateDescription = playing
            },
    ) {
        Icon(painterResource(if (sampling) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (sampling) R.string.voices_stop else R.string.voices_listen),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun UseButton(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier.described(stringResource(R.string.voices_use_voice, name))) {
        Text(stringResource(R.string.voices_use), style = MaterialTheme.typography.labelLarge)
    }
}

/** For a card's button: at least 56 dp high, and read by TalkBack as [description], which names the voice. */
internal fun Modifier.described(description: String): Modifier =
    heightIn(min = 56.dp).semantics { contentDescription = description }

/** "Alex", "Alex and Santa", "Alex, Dora and Santa". */
@Composable
internal fun joinedNames(names: List<String>): String =
    if (names.size == 1) {
        names.single()
    } else {
        stringResource(R.string.voices_and, names.dropLast(1).joinToString(", "), names.last())
    }

@Composable
internal fun secondaryTextColor(inUse: Boolean): Color =
    if (inUse) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

/** The message for a voice download that failed. */
internal fun VoiceDownloadFailure.messageRes(): Int =
    when (this) {
        VoiceDownloadFailure.NETWORK -> R.string.voices_failure_network
        VoiceDownloadFailure.UNAVAILABLE -> R.string.voices_failure_unavailable
        VoiceDownloadFailure.DAMAGED -> R.string.voices_failure_damaged
        VoiceDownloadFailure.STORAGE -> R.string.voices_failure_storage
    }
