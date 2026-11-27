package com.giovannimartins.rreader.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.BuildConfig
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.components.ScreenSurface

/** The About and licences screen over the app ([AboutViewModel]); back closes a licence text, then the screen. */
@Composable
fun AboutRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val licence by viewModel.licence.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val open = licence
    if (open != null) {
        BackHandler(onBack = viewModel::closeLicence)
        LicenceScreen(licence = open, onClose = viewModel::closeLicence, modifier = modifier)
    } else {
        BackHandler(onBack = onClose)
        AboutScreen(
            version = BuildConfig.VERSION_NAME,
            onReadLicence = viewModel::read,
            onOpenLink = { url -> openInBrowser(context, url) },
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/**
 * About and licences: the app's version, the address of its source code (selectable, and opened in the browser on
 * request), why the installed app is under the GPL 3.0, every component with its licence, and the terms of the voice
 * models. Plain, large text; reached from the Voices tab, outside the main flow.
 */
@Composable
fun AboutScreen(
    version: String,
    onReadLicence: (AboutComponent) -> Unit,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSurface(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TopBar(title = stringResource(R.string.about_title), onClose = onClose) }
            item { SourceCode(version = version, onOpenLink = onOpenLink) }
            item { SectionTitle(stringResource(R.string.about_components)) }
            items(AboutContent.components) { component ->
                ComponentCard(component = component, onReadLicence = onReadLicence, onOpenLink = onOpenLink)
            }
            item { SectionTitle(stringResource(R.string.about_voices)) }
            item { Paragraph(stringResource(R.string.about_voices_intro)) }
            items(AboutContent.voiceTerms) { (terms, component) ->
                ComponentCard(
                    component = component,
                    onReadLicence = onReadLicence,
                    onOpenLink = onOpenLink,
                    text = stringResource(terms),
                )
            }
            item { Paragraph(stringResource(R.string.about_terms_note)) }
        }
    }
}

/** The full text of one licence, paragraph by paragraph. */
@Composable
fun LicenceScreen(
    licence: OpenLicence,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSurface(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TopBar(title = licence.component.licence, onClose = onClose) }
            when {
                licence.failed -> item { Paragraph(stringResource(R.string.about_licence_failed)) }
                licence.paragraphs.isEmpty() -> item { CircularProgressIndicator() }
                else -> items(licence.paragraphs) { paragraph -> Paragraph(paragraph) }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.about_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp).semantics { heading() },
        )
    }
}

@Composable
private fun SourceCode(
    version: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Paragraph(stringResource(R.string.about_version, version))
        Paragraph(stringResource(R.string.about_free))
        SelectionContainer {
            Text(
                text = AboutContent.SOURCE_URL,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedButton(onClick = { onOpenLink(AboutContent.SOURCE_URL) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.about_open_source), style = MaterialTheme.typography.labelLarge)
        }
        Paragraph(stringResource(R.string.about_gpl))
    }
}

@Composable
private fun ComponentCard(
    component: AboutComponent,
    onReadLicence: (AboutComponent) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val name = if (component.nameRes != 0) stringResource(component.nameRes) else component.name
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = text ?: name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = component.licence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val asset = component.asset
            val url = component.url
            if (asset != null) {
                val description = stringResource(R.string.about_read_licence_of, name)
                OutlinedButton(
                    onClick = { onReadLicence(component) },
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.about_read_licence), style = MaterialTheme.typography.labelLarge)
                }
            } else if (url != null) {
                val description = stringResource(R.string.about_open_terms_of, name)
                OutlinedButton(
                    onClick = { onOpenLink(url) },
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.about_open_terms), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Paragraph(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/** Opens [url] in the browser the listener uses; nothing happens when the phone has none. */
private fun openInBrowser(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Diagnostics.recordHandled("open a web page", e)
    }
}
