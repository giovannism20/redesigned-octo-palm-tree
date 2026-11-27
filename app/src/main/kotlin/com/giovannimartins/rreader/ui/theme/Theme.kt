package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RReaderColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        background = Ink,
        onBackground = OnInk,
        surface = Surface,
        onSurface = OnInk,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = OnInkMuted,
        error = Danger,
    )

/** R Reader's Material 3 theme. Dark only for now (see docs/product.md). */
@Composable
fun RReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RReaderColors,
        typography = RReaderTypography,
        content = content,
    )
}
