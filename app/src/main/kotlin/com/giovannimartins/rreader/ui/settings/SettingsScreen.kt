package com.giovannimartins.rreader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.giovannimartins.rreader.ui.theme.readingTextStyle

/** Stateful Ajustes tab bound to [SettingsViewModel]. */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    SettingsScreen(
        appearance = appearance ?: Appearance(),
        onTextSizeChange = viewModel::setTextSize,
        onThemeChange = viewModel::setTheme,
        onReadingFontChange = viewModel::setReadingFont,
        modifier = modifier,
    )
}

/**
 * The Ajustes tab: the text size of every screen with a preview, the theme, and the font of the follow-along text.
 * Every choice is a large row of a radio group; the chosen one has a filled radio button, a border and a check mark,
 * never colour alone. A choice applies at once to the whole app, this screen and its preview included.
 */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onTextSizeChange: (TextSize) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onReadingFontChange: (ReadingFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        item { SectionHeading(R.string.settings_text_size, R.string.settings_text_size_intro) }
        item { TextSizePreview() }
        item {
            ChoiceGroup(TextSize.entries, appearance.textSize, onTextSizeChange) { size, _ ->
                ChoiceLabel(stringResource(size.label))
            }
        }
        item { SectionHeading(R.string.settings_theme, R.string.settings_theme_intro) }
        item {
            ChoiceGroup(ThemeChoice.entries, appearance.theme, onThemeChange) { theme, selected ->
                ChoiceLabel(stringResource(theme.label))
                ChoiceDescription(stringResource(theme.description), selected)
            }
        }
        item { SectionHeading(R.string.settings_reading_font, R.string.settings_reading_font_intro) }
        item {
            ChoiceGroup(ReadingFont.entries, appearance.readingFont, onReadingFontChange) { font, selected ->
                ChoiceLabel(stringResource(font.label))
                ChoiceDescription(stringResource(font.description), selected)
                Text(text = stringResource(R.string.reading_font_sample), style = readingTextStyle(font, scale = 1f))
            }
        }
    }
}

/** A section's heading and the short text under it. */
@Composable
private fun SectionHeading(
    @StringRes title: Int,
    @StringRes text: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A sentence and a button as the app draws them at the chosen size; the button sample does nothing. */
@Composable
private fun TextSizePreview(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_preview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = stringResource(R.string.settings_preview_text), style = MaterialTheme.typography.bodyLarge)
            Surface(
                shape = ButtonDefaults.shape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.library_listen), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One radio row per option; [content] draws an option's words, told whether it is the chosen one. */
@Composable
private fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(option: T, selected: Boolean) -> Unit,
) {
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val chosen = option == selected
            ChoiceRow(selected = chosen, onClick = { onSelect(option) }) { content(option, chosen) }
        }
    }
}

/**
 * A large row that TalkBack reads as a radio button with its words. The chosen one is on the selection colour with a
 * 2 dp border, a filled radio button and a check mark.
 */
@Composable
private fun ChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.testTag(CHOSEN_MARK_TAG),
                )
            }
        }
    }
}

@Composable
private fun ChoiceLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun ChoiceDescription(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Tags the check mark of a chosen row, so tests can see that the choice is shown by more than colour. */
internal const val CHOSEN_MARK_TAG = "chosen-mark"

@get:StringRes
private val TextSize.label: Int
    get() =
        when (this) {
            TextSize.NORMAL -> R.string.text_size_normal
            TextSize.LARGE -> R.string.text_size_large
            TextSize.LARGER -> R.string.text_size_larger
            TextSize.LARGEST -> R.string.text_size_largest
        }

@get:StringRes
private val ThemeChoice.label: Int
    get() =
        when (this) {
            ThemeChoice.DARK -> R.string.theme_dark
            ThemeChoice.LIGHT -> R.string.theme_light
            ThemeChoice.PHONE -> R.string.theme_phone
        }

@get:StringRes
private val ThemeChoice.description: Int
    get() =
        when (this) {
            ThemeChoice.DARK -> R.string.theme_dark_description
            ThemeChoice.LIGHT -> R.string.theme_light_description
            ThemeChoice.PHONE -> R.string.theme_phone_description
        }

@get:StringRes
private val ReadingFont.label: Int
    get() =
        when (this) {
            ReadingFont.ATKINSON -> R.string.reading_font_atkinson
            ReadingFont.LITERATA -> R.string.reading_font_literata
        }

@get:StringRes
private val ReadingFont.description: Int
    get() =
        when (this) {
            ReadingFont.ATKINSON -> R.string.reading_font_atkinson_description
            ReadingFont.LITERATA -> R.string.reading_font_literata_description
        }

@Preview
@Composable
private fun SettingsScreenPreview() {
    RReaderTheme(darkTheme = false) {
        ScreenSurface {
            SettingsScreen(
                appearance = Appearance(textSize = TextSize.LARGE),
                onTextSizeChange = {},
                onThemeChange = {},
                onReadingFontChange = {},
            )
        }
    }
}
