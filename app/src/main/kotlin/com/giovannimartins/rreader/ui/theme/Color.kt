package com.giovannimartins.rreader.ui.theme

import androidx.compose.ui.graphics.Color

// Palette derived from the "Livro Aberto" logo (docs/design/brand.md): night blue, paper and sealing-wax red. The dark
// scheme, the default, draws paper-coloured text on ink; the light scheme draws ink text on paper. Every text and
// background pair of both schemes meets WCAG AA (ratios in brand.md, checked by ThemeTest).

/** The darkest night blue: the dark scheme's background, and the text of the light scheme. */
internal val Ink = Color(0xFF0E1526)

/** The logo's paper: the light scheme's background, and the text of the dark scheme. */
internal val Paper = Color(0xFFF5EAD6)

// Dark scheme.

internal val Surface = Color(0xFF18233F)
internal val SurfaceHigh = Color(0xFF22304F)
internal val OnInk = Paper
internal val OnInkMuted = Color(0xFFC8BEAD)
internal val Seal = Color(0xFFEE7A5F)
internal val OnSeal = Ink
internal val SealContainer = Color(0xFF4A2B2A)
internal val Outline = Color(0xFF8792AE)
internal val Danger = Color(0xFFFFB4A9)

// Supporting tones so that every Material colour role maps to the brand (see RReaderDarkColorScheme).

/**
 * Material's legacy surfaceVariant role, between [Surface] and [SurfaceHigh]. It must differ from the container
 * tones: Material picks a surface's content colour by its value, and surfaceVariant gets the muted text colour.
 */
internal val SurfaceMid = Color(0xFF1E2B49)

/**
 * Sealing wax deep enough for text and buttons on paper: the light scheme's accent, and actions on the dark
 * scheme's paper-coloured inverse surface, such as snackbar buttons.
 */
internal val SealDeep = Color(0xFF9C3A24)

/** Container for error messages; paper text on it. */
internal val DangerContainer = Color(0xFF5C2020)

/** Decorative dividers and disabled borders only; never for text or for borders that carry meaning. */
internal val OutlineVariant = Color(0xFF3A4766)

// Light scheme: the same roles on paper (see RReaderLightColorScheme).

/** Bars, sheets and menus on paper: paper in a little shade. */
internal val PaperShade = Color(0xFFEEE1C7)

/** The light scheme's legacy surfaceVariant role: like [SurfaceMid], it must differ from the container tones. */
internal val PaperMid = Color(0xFFEBDDC1)

/** Raised areas on paper: cards, dialogs, the mini player. */
internal val PaperHigh = Color(0xFFE8D8BA)

/** Decorative dividers and disabled borders on paper only; never for text. */
internal val PaperDeep = Color(0xFFD9C8A9)

/** Text on paper: ink. */
internal val OnPaper = Ink

/** Secondary text on paper: ink thinned toward slate. */
internal val OnPaperMuted = Color(0xFF4E5360)

/** Selected items on paper (always with a 2 dp [SealDeep] border), the active tab and progress tracks. */
internal val SealTint = Color(0xFFF2D5C6)

/** Borders on paper. */
internal val OutlineDeep = Color(0xFF5F6577)

/** Errors on paper. */
internal val DangerDeep = Color(0xFFA4291F)

/** Container for error messages on paper; ink text on it. */
internal val DangerTint = Color(0xFFF6D3CB)

// Both schemes.

/** Generated covers (books without a cover image): chosen from the title, with [OnCover] text. */
internal val CoverColors =
    listOf(Color(0xFF3B4A6B), Color(0xFF5B3B4A), Color(0xFF2F5A4E), Color(0xFF6B4E2F), Color(0xFF4A3B6B))
internal val OnCover = Color(0xFFFFFFFF)

/**
 * The colours of the voice emblems (abstract marks, one per voice, never a face): brand tones, each at least 3 : 1
 * against the night-blue [Surface] disc they are drawn on in both schemes, and against the [SurfaceHigh] cards around
 * it in the dark one (checked by ThemeTest). Every emblem also has a shape of its own, so voices never differ by colour
 * alone.
 */
internal val VoiceEmblemColors = listOf(Seal, OnInk, Outline, Danger, OnInkMuted)
