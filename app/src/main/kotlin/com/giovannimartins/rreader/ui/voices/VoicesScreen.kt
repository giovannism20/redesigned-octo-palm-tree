package com.giovannimartins.rreader.ui.voices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.Voice

/** Stateful voices screen bound to [VoicesViewModel]. */
@Composable
fun VoicesRoute(
    modifier: Modifier = Modifier,
    viewModel: VoicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sampleText = stringResource(R.string.voices_sample_text)
    val context = LocalContext.current
    VoicesScreen(
        state = state,
        onSample = { viewModel.playSample(it, sampleText) },
        onSelect = viewModel::select,
        onOpenSystemSettings = { openTextToSpeechSettings(context) },
        modifier = modifier,
    )
}

/** Lists the phone's Portuguese voices with a sample button and a way to choose the default. */
@Composable
fun VoicesScreen(
    state: VoicesUiState,
    onSample: (Voice) -> Unit,
    onSelect: (Voice) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.voices_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(
                text = stringResource(R.string.voices_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.loading -> {
                item { CircularProgressIndicator() }
            }

            state.voices.isEmpty() -> {
                item { NoVoices(onOpenSystemSettings) }
            }

            else -> {
                itemsIndexed(state.voices, key = { _, voice -> voice.id }) { index, voice ->
                    VoiceCard(
                        voice = voice,
                        number = index + 1,
                        selected = voice.id == state.selectedVoiceId,
                        preparing = voice.id == state.preparingSampleId,
                        onSample = { onSample(voice) },
                        onSelect = { onSelect(voice) },
                    )
                }
            }
        }
        if (state.sampleFailed) {
            item {
                Text(
                    text = stringResource(R.string.voices_sample_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state.voices.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onOpenSystemSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.voices_more), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: Voice,
    number: Int,
    selected: Boolean,
    preparing: Boolean,
    onSample: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    // The selected voice is marked by a border as well as colour, so it does not rely on colour alone.
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text =
                    stringResource(
                        R.string.voices_voice_name,
                        number,
                        voice.locale.getDisplayCountry(LocalConfiguration.current.locales[0]),
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    stringResource(
                        if (voice.requiresNetwork) R.string.voices_needs_internet else R.string.voices_offline,
                        voice.engineLabel,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSample, enabled = !preparing, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.voices_listen), style = MaterialTheme.typography.labelLarge)
                }
                if (selected) {
                    Text(
                        text = stringResource(R.string.voices_in_use),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = onSelect, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.voices_use), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoVoices(
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.voices_none), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenSystemSettings, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.voices_install), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Opens the system text-to-speech settings, where voices can be downloaded. */
private fun openTextToSpeechSettings(context: Context) {
    val tts = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(tts)
    } catch (e: ActivityNotFoundException) {
        Diagnostics.recordHandled("open TTS settings", e)
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
