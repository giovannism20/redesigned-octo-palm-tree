package com.giovannimartins.rreader.ui.voices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.NeuralVoice

/** Stateful Voices tab bound to [VoicesViewModel]; samples stop when the tab is left. */
@Composable
fun VoicesRoute(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sampleText = stringResource(R.string.voices_sample_text)
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPhoneVoices()
        onPauseOrDispose { viewModel.stopSample() }
    }
    VoicesScreen(
        state = state,
        onToggleSample = viewModel::toggleSample,
        onTogglePhoneSample = { voice -> viewModel.togglePhoneSample(voice, sampleText) },
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onUse = viewModel::use,
        onDelete = viewModel::askDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = { viewModel.askDelete(null) },
        onAnswerMobileData = viewModel::answerMobileData,
        onOpenSystemSettings = { openTextToSpeechSettings(context) },
        onOpenAbout = onOpenAbout,
        modifier = modifier,
    )
}

/**
 * The Voices tab: the nine neural voices, most recommended first, each with its emblem, a short description, a
 * bundled sample and its download; the phone's own voice; and, folded away, the phone's other voices by distinct
 * names. The voice in use has a border and the words "In use", never colour alone.
 */
@Composable
fun VoicesScreen(
    state: VoicesUiState,
    onToggleSample: (NeuralVoice) -> Unit,
    onTogglePhoneSample: (Voice?) -> Unit,
    onDownload: (NeuralVoice) -> Unit,
    onCancelDownload: (NeuralVoice) -> Unit,
    onUse: (String) -> Unit,
    onDelete: (NeuralVoice) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onAnswerMobileData: (Boolean?) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOthers by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Heading(R.string.voices_title, R.string.voices_intro, MaterialTheme.typography.headlineLarge) }
        item {
            Heading(
                R.string.voices_neural_title,
                R.string.voices_neural_intro,
                MaterialTheme.typography.titleLarge,
            )
        }
        items(state.neural, key = { it.voice.id }) { card ->
            NeuralVoiceItem(
                card = card,
                sampling = state.sampleVoiceId == card.voice.id,
                onToggleSample = { onToggleSample(card.voice) },
                onDownload = { onDownload(card.voice) },
                onCancelDownload = { onCancelDownload(card.voice) },
                onUse = { onUse(card.voice.id) },
                onDelete = { onDelete(card.voice) },
            )
        }
        phoneVoices(
            state = state,
            showOthers = showOthers,
            onToggleOthers = { showOthers = !showOthers },
            onTogglePhoneSample = onTogglePhoneSample,
            onUse = onUse,
            onOpenSystemSettings = onOpenSystemSettings,
        )
        item {
            TextButton(onClick = onOpenAbout, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_about), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    state.askMobileData?.let { voice -> MobileDataDialog(voice = voice, onAnswer = onAnswerMobileData) }
    state.askDelete?.let { voice ->
        DeleteDialog(voice = voice, onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
    }
}

/** A heading of the tab and the short text under it. */
@Composable
private fun Heading(
    title: Int,
    text: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(title), style = style, modifier = Modifier.semantics { heading() })
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The phone's voice, a failed sample, and the phone's other voices, folded away under a toggle. */
private fun LazyListScope.phoneVoices(
    state: VoicesUiState,
    showOthers: Boolean,
    onToggleOthers: () -> Unit,
    onTogglePhoneSample: (Voice?) -> Unit,
    onUse: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    item {
        PhoneVoiceItem(
            state = state,
            onToggleSample = { onTogglePhoneSample(null) },
            onUse = { onUse(VoiceIds.PHONE) },
            onOpenSystemSettings = onOpenSystemSettings,
        )
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
    item { OtherPhoneVoicesToggle(expanded = showOthers, onToggle = onToggleOthers) }
    if (showOthers) {
        items(state.otherPhoneVoices, key = { it.label.voice.id }) { card ->
            PhoneVoiceRow(
                card = card,
                sampling = state.sampleVoiceId == card.label.voice.id,
                onToggleSample = { onTogglePhoneSample(card.label.voice) },
                onUse = { onUse(card.label.voice.id) },
            )
        }
        item {
            OutlinedButton(onClick = onOpenSystemSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.voices_more), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
