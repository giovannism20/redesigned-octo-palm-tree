package com.giovannimartins.rreader.ui.theme

import androidx.compose.ui.graphics.Color

// Palette derived from the "Livro Aberto" logo (docs/design/brand.md): night blue,
// paper and sealing-wax red. Dark by default with high contrast for long listening
// sessions; every text/background pair meets WCAG AA (ratios in brand.md).
internal val Ink = Color(0xFF0E1526)
internal val Surface = Color(0xFF18233F)
internal val SurfaceHigh = Color(0xFF22304F)
internal val OnInk = Color(0xFFF5EAD6)
internal val OnInkMuted = Color(0xFFC8BEAD)
internal val Seal = Color(0xFFEE7A5F)
internal val OnSeal = Color(0xFF0E1526)
internal val SealContainer = Color(0xFF4A2B2A)
internal val Outline = Color(0xFF8792AE)
internal val Danger = Color(0xFFFFB4A9)

// Supporting tones so that every Material colour role maps to the brand (see RReaderColorScheme).

/**
 * Material's legacy surfaceVariant role, between [Surface] and [SurfaceHigh]. It must differ from the container
 * tones: Material picks a surface's content colour by its value, and surfaceVariant gets the muted text colour.
 */
internal val SurfaceMid = Color(0xFF1E2B49)

/** Sealing wax deep enough for actions on the paper-coloured inverse surface, such as snackbar buttons. */
internal val SealDeep = Color(0xFFB4442B)

/** Container for error messages; paper text on it. */
internal val DangerContainer = Color(0xFF5C2020)

/** Decorative dividers and disabled borders only; never for text or for borders that carry meaning. */
internal val OutlineVariant = Color(0xFF3A4766)

/** Generated covers (books without a cover image): chosen from the title, with [OnCover] text. */
internal val CoverColors =
    listOf(Color(0xFF3B4A6B), Color(0xFF5B3B4A), Color(0xFF2F5A4E), Color(0xFF6B4E2F), Color(0xFF4A3B6B))
internal val OnCover = Color(0xFFFFFFFF)
