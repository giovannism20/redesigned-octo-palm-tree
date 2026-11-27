package com.giovannimartins.rreader.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.voices.VoiceEmblem
import com.giovannimartins.rreader.ui.voices.VoiceLooks

/** The player's "Voice" button, which opens the [VoiceChooserSheet]; a choice or "more voices" closes it. */
@Composable
internal fun VoiceButton(
    menu: VoiceMenu,
    onChoose: (voiceId: String, forAllBooks: Boolean) -> Unit,
    onMoreVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(painterResource(R.drawable.ic_voices), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.player_voice), style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        VoiceChooserSheet(
            menu = menu,
            onChoose = { voiceId, forAllBooks ->
                open = false
                onChoose(voiceId, forAllBooks)
            },
            onMoreVoices = {
                open = false
                onMoreVoices()
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * The player's voice chooser: the voices ready on the phone, with the same emblems and names as the Voices tab, the
 * one reading the book marked "In use". Choosing another asks whether it is for this book only or for every book;
 * either way it takes over at the sentence being read. "Download more voices" opens the Voices tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceChooserSheet(
    menu: VoiceMenu,
    onChoose: (voiceId: String, forAllBooks: Boolean) -> Unit,
    onMoreVoices: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        val choice = menu.choices.firstOrNull { it.voiceId == pending }
        if (choice == null) {
            VoiceList(menu = menu, onPick = { pending = it.voiceId }, onMoreVoices = onMoreVoices)
        } else {
            VoiceScope(
                name = choice.name(),
                onChoose = { forAllBooks -> onChoose(choice.voiceId, forAllBooks) },
                onBack = { pending = null },
            )
        }
    }
}

@Composable
private fun VoiceList(
    menu: VoiceMenu,
    onPick: (VoiceChoice) -> Unit,
    onMoreVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                text = stringResource(R.string.player_voice_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        items(menu.choices, key = VoiceChoice::voiceId) { choice ->
            VoiceRow(choice = choice, onClick = { onPick(choice) })
        }
        item {
            OutlinedButton(
                onClick = onMoreVoices,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.player_voice_more), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A voice of the list; the one in use has a border and says "In use". */
@Composable
private fun VoiceRow(
    choice: VoiceChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = choice.neural?.let(VoiceLooks::of) ?: VoiceLooks.PHONE
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (choice.inUse) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (choice.inUse) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = !choice.inUse, role = Role.Button, onClick = onClick)
                    .semantics { selected = choice.inUse }
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceEmblem(look = look, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = choice.name(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(look.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (choice.inUse) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            if (choice.inUse) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.voices_in_use),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** "Read with Dora:" this book only, or every book. */
@Composable
private fun VoiceScope(
    name: String,
    onChoose: (forAllBooks: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.player_voice_question, name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Button(onClick = { onChoose(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.player_voice_this_book), style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = { onChoose(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.player_voice_all_books), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.player_voice_back), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VoiceChoice.name(): String = neural?.name ?: stringResource(R.string.voices_phone_name)
