package com.giovannimartins.rreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice

/**
 * Every Material colour role mapped to the brand palette on ink (docs/design/brand.md). Roles left out would fall
 * back to Material's default purples and greys, which components such as cards, the navigation bar, progress bars,
 * sheets and snackbars use, so none is left out (enforced by ThemeTest).
 */
internal val RReaderDarkColorScheme =
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
        surfaceVariant = SurfaceMid,
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

/**
 * The same roles on paper: ink text, a sealing wax deep enough for paper, and raised areas in slightly darker paper.
 * The fixed roles keep the dark scheme's colours, as Material intends them to stay the same in both schemes, and the
 * inverse surface (snackbars) is the logo's night blue.
 */
internal val RReaderLightColorScheme =
    lightColorScheme(
        primary = SealDeep,
        onPrimary = Paper,
        primaryContainer = SealTint,
        onPrimaryContainer = OnPaper,
        inversePrimary = Seal,
        secondary = SealDeep,
        onSecondary = Paper,
        secondaryContainer = SealTint,
        onSecondaryContainer = OnPaper,
        tertiary = SealDeep,
        onTertiary = Paper,
        tertiaryContainer = SealTint,
        onTertiaryContainer = OnPaper,
        background = Paper,
        onBackground = OnPaper,
        surface = PaperShade,
        onSurface = OnPaper,
        surfaceVariant = PaperMid,
        onSurfaceVariant = OnPaperMuted,
        surfaceTint = OutlineDeep,
        inverseSurface = Surface,
        inverseOnSurface = Paper,
        error = DangerDeep,
        onError = Paper,
        errorContainer = DangerTint,
        onErrorContainer = OnPaper,
        outline = OutlineDeep,
        outlineVariant = PaperDeep,
        scrim = Color.Black,
        surfaceBright = Paper,
        surfaceContainer = PaperShade,
        surfaceContainerHigh = PaperHigh,
        surfaceContainerHighest = PaperHigh,
        surfaceContainerLow = PaperShade,
        surfaceContainerLowest = Paper,
        surfaceDim = PaperHigh,
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

/** The colour scheme of the dark or the light theme. */
internal fun colorSchemeFor(darkTheme: Boolean): ColorScheme =
    if (darkTheme) RReaderDarkColorScheme else RReaderLightColorScheme

/** Whether this choice shows the dark theme now: [ThemeChoice.PHONE] follows the phone's dark theme setting. */
@Composable
@ReadOnlyComposable
fun ThemeChoice.isDark(): Boolean =
    when (this) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.PHONE -> isSystemInDarkTheme()
    }

/**
 * R Reader's Material 3 theme, built from the brand palette and type scale: dark by default, or light. [textSize]
 * multiplies the phone's own font scale for everything inside, so every text in sp grows with it (dialogs, sheets and
 * menus, which open in windows of their own, apply it again: see ui/components/AppWindows.kt).
 */
@Composable
fun RReaderTheme(
    darkTheme: Boolean = true,
    textSize: TextSize = TextSize.NORMAL,
    content: @Composable () -> Unit,
) {
    val phone = LocalDensity.current
    val scaled = Density(density = phone.density, fontScale = phone.fontScale * textSize.scale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = colorSchemeFor(darkTheme),
            typography = RReaderTypography,
            content = content,
        )
    }
}
