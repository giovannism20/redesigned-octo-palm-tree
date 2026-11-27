package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RReaderColors =
    darkColorScheme(
        primary = Seal,
        onPrimary = OnSeal,
        primaryContainer = SealContainer,
        onPrimaryContainer = OnInk,
        background = Ink,
        onBackground = OnInk,
        surface = Surface,
        onSurface = OnInk,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = OnInkMuted,
        surfaceContainer = Surface,
        surfaceContainerHigh = SurfaceHigh,
        outline = Outline,
        error = Danger,
    )

/** R Reader's Material 3 theme, built from the brand palette. Dark only (see docs/product.md). */
@Composable
fun RReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RReaderColors,
        typography = RReaderTypography,
        content = content,
    )
}
