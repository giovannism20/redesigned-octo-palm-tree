package com.giovannimartins.rreader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Dialogs, bottom sheets and menus open in windows of their own, whose content gets the phone's density back and
// would show its text at the phone's size only. These read the density where they open, which carries the listener's
// text size (RReaderTheme), and apply it again inside. Screens use them instead of Material's.

/**
 * Material's alert dialog at the listener's text size. Its text scrolls when, at the largest sizes, it is taller than
 * the dialog, so the buttons always stay in view.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { WithDensity(density, confirmButton) },
        modifier = modifier,
        dismissButton = dismissButton?.let { button -> @Composable { WithDensity(density, button) } },
        title = { WithDensity(density, title) },
        text = { WithDensity(density) { Box(Modifier.verticalScroll(rememberScrollState())) { text() } } },
    )
}

/** Material's modal bottom sheet at the listener's text size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        WithDensity(density) { content() }
    }
}

/** Material's dropdown menu at the listener's text size. */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        WithDensity(density) { content() }
    }
}

@Composable
private fun WithDensity(
    density: Density,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
