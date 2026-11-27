package com.giovannimartins.rreader.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.theme.RReaderTheme

/** MIME types offered in the system picker; content is still verified by signature. */
private val PICKER_MIME_TYPES = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

/**
 * Explains how to add a book and opens the system file picker. A page of the library, opened by its "Add a book"
 * action; [onClose] goes back to the books.
 */
@Composable
fun ImportScreen(
    receiving: Boolean,
    onFileSelect: (android.net.Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onFileSelect(uri)
        }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.import_back),
                )
            }
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 8.dp).semantics { heading() },
            )
        }
        Text(text = stringResource(R.string.import_body), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { picker.launch(PICKER_MIME_TYPES) },
            enabled = !receiving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            if (receiving) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            }
            Text(text = stringResource(R.string.import_choose), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = stringResource(R.string.import_tip_share),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun ImportScreenPreview() {
    RReaderTheme {
        ScreenSurface { ImportScreen(receiving = false, onFileSelect = {}, onClose = {}) }
    }
}
