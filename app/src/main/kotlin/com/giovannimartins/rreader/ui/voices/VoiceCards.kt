package com.giovannimartins.rreader.ui.voices

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NeuralVoiceItem(
    card: NeuralVoiceCard,
    sampling: Boolean,
    onToggleSample: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = card.voice.name
    val size = Formatter.formatShortFileSize(LocalContext.current, card.voice.pkg.archiveBytes)
    VoiceCard(inUse = card.inUse, modifier = modifier) {
        VoiceHeading(look = VoiceLooks.of(card.voice), name = name, inUse = card.inUse)
        DownloadStatus(card = card)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleButton(name = name, sampling = sampling, onClick = onToggleSample)
            when (card.status) {
                NeuralVoiceStatus.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.described(stringResource(R.string.voices_download_voice, name, size)),
                    ) {
                        Text(
                            stringResource(R.string.voices_download, size),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                is NeuralVoiceStatus.Waiting, is NeuralVoiceStatus.Downloading -> {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.described(stringResource(R.string.voices_cancel_download, name)),
                    ) {
                        Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Being unpacked: nothing to do but wait.
                NeuralVoiceStatus.Installing -> {}

                is NeuralVoiceStatus.Failed -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.described(stringResource(R.string.voices_retry_voice, name)),
                    ) {
                        Text(stringResource(R.string.voices_retry), style = MaterialTheme.typography.labelLarge)
                    }
                }

                NeuralVoiceStatus.Ready -> {
                    if (!card.inUse) UseButton(name = name, onClick = onUse)
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.described(stringResource(R.string.voices_delete_voice, name)),
                    ) {
                        Text(stringResource(R.string.voices_delete), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Where the download of a voice stands, or what else its download brings; nothing once it is ready. */
@Composable
private fun DownloadStatus(
    card: NeuralVoiceCard,
    modifier: Modifier = Modifier,
) {
    val muted = secondaryTextColor(card.inUse)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val status = card.status) {
            NeuralVoiceStatus.NotDownloaded -> {
                sharedDownload(card)?.let { text ->
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }

            is NeuralVoiceStatus.Waiting -> {
                val text =
                    stringResource(if (status.wifiOnly) R.string.voices_waiting_wifi else R.string.voices_waiting)
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = muted)
            }

            is NeuralVoiceStatus.Downloading -> {
                val percent = (status.fraction * PERCENT).roundToInt()
                Text(
                    text = stringResource(R.string.voices_downloading, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
            }

            NeuralVoiceStatus.Installing -> {
                Text(
                    text = stringResource(R.string.voices_installing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is NeuralVoiceStatus.Failed -> {
                Text(
                    text = stringResource(status.reason.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // A ready voice has nothing more to say about its download.
            NeuralVoiceStatus.Ready -> {}
        }
    }
}

/** "The download also brings Alex and Santa", for voices that share their download; null for the others. */
@Composable
private fun sharedDownload(card: NeuralVoiceCard): String? =
    card.sharesWith.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.voices_download_also, joinedNames(it)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneVoiceItem(
    state: VoicesUiState,
    onToggleSample: () -> Unit,
    onUse: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(R.string.voices_phone_name)
    VoiceCard(inUse = state.phoneInUse, modifier = modifier) {
        VoiceHeading(look = VoiceLooks.PHONE, name = name, inUse = state.phoneInUse)
        if (state.phoneVoiceAvailable == false) {
            Text(
                text = stringResource(R.string.voices_none),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor(state.phoneInUse),
            )
            Button(onClick = onOpenSystemSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_install), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleButton(name = name, sampling = state.sampleVoiceId == VoiceIds.PHONE, onClick = onToggleSample)
                if (!state.phoneInUse) UseButton(name = name, onClick = onUse)
            }
        }
    }
}

/** A phone voice of the "Other phone voices" section: "Google · Voice 1", its country and its use of the internet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneVoiceRow(
    card: PhoneVoiceCard,
    sampling: Boolean,
    onToggleSample: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = card.label.voice
    val name = stringResource(R.string.voices_phone_voice_label, card.label.engine, card.label.number)
    val country = voice.locale.getDisplayCountry(LocalConfiguration.current.locales[0])
    VoiceCard(inUse = card.inUse, modifier = modifier) {
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        val availability = if (voice.requiresNetwork) R.string.voices_needs_internet else R.string.voices_offline
        Text(
            text = stringResource(availability, country),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor(card.inUse),
        )
        if (card.inUse) InUseLabel()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleButton(name = name, sampling = sampling, onClick = onToggleSample)
            if (!card.inUse) UseButton(name = name, onClick = onUse)
        }
    }
}

@Composable
internal fun OtherPhoneVoicesToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onToggle, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(
            text = stringResource(R.string.voices_other_phone),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
        )
    }
}

private const val PERCENT = 100
