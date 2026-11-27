package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Every Material colour role mapped to the brand palette (docs/design/brand.md). Roles left out would fall
 * back to Material's default purples and greys, which components such as cards, the navigation bar,
 * progress bars, sheets and snackbars use, so none is left out (enforced by ThemeTest).
 */
internal val RReaderColorScheme =
    darkColorScheme(
        primary = Seal,
        onPrimary = OnSeal,
        primaryContainer = SealContainer,
        onPrimaryContainer = OnInk,
        inversePrimary = SealDeep,
        secondary = Seal,
        onSecondary = OnSeal,
        secondaryContainer = SealContainer,
        onSecondaryContainer = OnInk,
        tertiary = Seal,
        onTertiary = OnSeal,
        tertiaryContainer = SealContainer,
        onTertiaryContainer = OnInk,
        background = Ink,
        onBackground = OnInk,
        surface = Surface,
        onSurface = OnInk,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = OnInkMuted,
        surfaceTint = Outline,
        inverseSurface = OnInk,
        inverseOnSurface = Ink,
        error = Danger,
        onError = Ink,
        errorContainer = DangerContainer,
        onErrorContainer = OnInk,
        outline = Outline,
        outlineVariant = OutlineVariant,
        scrim = Color.Black,
        surfaceBright = SurfaceHigh,
        surfaceContainer = Surface,
        surfaceContainerHigh = SurfaceHigh,
        surfaceContainerHighest = SurfaceHigh,
        surfaceContainerLow = Surface,
        surfaceContainerLowest = Ink,
        surfaceDim = Ink,
        primaryFixed = Seal,
        primaryFixedDim = Seal,
        onPrimaryFixed = OnSeal,
        onPrimaryFixedVariant = OnSeal,
        secondaryFixed = Seal,
        secondaryFixedDim = Seal,
        onSecondaryFixed = OnSeal,
        onSecondaryFixedVariant = OnSeal,
        tertiaryFixed = Seal,
        tertiaryFixedDim = Seal,
        onTertiaryFixed = OnSeal,
        onTertiaryFixedVariant = OnSeal,
    )

/** R Reader's Material 3 theme, built from the brand palette and type scale. Dark only (see docs/product.md). */
@Composable
fun RReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RReaderColorScheme,
        typography = RReaderTypography,
        content = content,
    )
}
