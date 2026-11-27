# Brand

## Logo: "Livro Aberto" (open book)

An R and a mirrored R stand back to back and share one stem. Their bowls are
the pages of an open book, and the shared stem becomes a bookmark ribbon —
the app's promise of never losing your place. It answers the owner's brief of
"an R overlapping another R" and was chosen by the owner on 2026-11-27 from
four concepts.

| Asset | File |
|-------|------|
| SVG sources (full, foreground, background, monochrome) | `docs/design/logo/r-reader-*.svg` |
| Adaptive icon foreground | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| Monochrome (themed icons, Android 13+) | `app/src/main/res/drawable/ic_launcher_monochrome.xml` |
| In-app mark (cropped to the emblem) | `app/src/main/res/drawable/ic_brand_mark.xml` |
| Adaptive icon | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` |
| Splash screen | `Theme.RReader.Starting` in `res/values/themes.xml` |

Geometry: 108 × 108 viewport; all essential content inside the 66 dp safe
circle (maximum radius 31.8). Plain paths with solid fills only, so the
VectorDrawables match the SVG sources exactly. The thinnest gaps
(≈ 1.6 px at 48 px) may close below about 36 px; do not use the mark smaller
than 36 dp.

## Logo colours

| Role | Name | Hex | Contrast with night blue |
|------|------|-----|--------------------------|
| Background | Night blue (Azul-noite) | `#18233F` | — |
| The two R's (pages) | Paper (Papel) | `#F5EAD6` | 13.0 : 1 |
| Ribbon | Sealing wax (Lacre) | `#E4664A` | 4.7 : 1 |

## App palette

Derived from the logo, in two schemes the listener chooses between in Ajustes
(see [Theme](#theme)): **dark** (the default: paper-coloured text on ink) and
**light** (ink text on paper). Every text pair of both meets WCAG AA.

### Dark (default)

The accent is a slightly lighter sealing wax so it reaches AA on every
background.

| Token (Compose) | Hex | Use | Contrast |
|-----------------|-----|-----|----------|
| `Ink` (background) | `#0E1526` | screen background, window background; also the light scheme's text | — |
| `Surface` | `#18233F` | cards, sheets (the logo's night blue); the light scheme's snackbars | — |
| `SurfaceHigh` | `#22304F` | raised surfaces: cards, dialogs, mini player | — |
| `OnInk` (= `Paper`) | `#F5EAD6` | body text | 15.3 on Ink, 13.0 on Surface |
| `OnInkMuted` | `#C8BEAD` | secondary text | 9.9 on Ink, 8.5 on Surface, 7.1 on SurfaceHigh |
| `Seal` (primary) | `#EE7A5F` | buttons, highlights, links | 6.6 on Ink, 5.6 on Surface |
| `OnSeal` | `#0E1526` | text on primary buttons | 6.6 on Seal |
| `SealContainer` | `#4A2B2A` | selected items (always with a 2 dp Seal border) | OnInk 10.6 on it |
| `Outline` | `#8792AE` | borders, dividers | 5.0 on Surface |
| `Danger` (error) | `#FFB4A9` | errors | 9.1 on Surface |

Supporting tokens, so every Material role has a brand colour:

| Token (Compose) | Hex | Use | Contrast |
|-----------------|-----|-----|----------|
| `SurfaceMid` | `#1E2B49` | Material's legacy surfaceVariant role only; kept distinct from the container tones because Material picks content colours by value | OnInkMuted 7.6 on it |
| `SealDeep` | `#9C3A24` | sealing wax deep enough for paper: actions on the paper-coloured inverse surface (snackbars); the light scheme's accent | 5.8 on OnInk |
| `DangerContainer` | `#5C2020` | error containers | OnInk 10.5 on it |
| `OutlineVariant` | `#3A4766` | decorative dividers, disabled borders (never text) | — |
| `CoverColors` / `OnCover` | five muted tones / white | generated covers of books without an image | ≥ 4.5 |
| `VoiceEmblemColors` | `Seal`, `OnInk`, `Outline`, `Danger`, `OnInkMuted` | the voice emblems' shapes, drawn on a `Surface` disc; every emblem also has its own shape | ≥ 3 : 1 on Surface and SurfaceHigh |

### Light

The same roles on the logo's paper: ink text, a sealing wax deep enough for
AA on every paper tone, and raised areas in slightly darker paper (as
Material's light containers get darker as they rise). Contrast is given on
the page (`Paper`) and on the darkest raised tone (`PaperHigh`).

| Token (Compose) | Hex | Use | Contrast |
|-----------------|-----|-----|----------|
| `Paper` (background) | `#F5EAD6` | screen background (the logo's paper) | — |
| `PaperShade` | `#EEE1C7` | navigation bar, sheets, menus | — |
| `PaperMid` | `#EBDDC1` | Material's legacy surfaceVariant only, apart from the container tones | OnPaperMuted 5.7 on it |
| `PaperHigh` | `#E8D8BA` | raised surfaces: cards, dialogs, mini player | — |
| `OnPaper` (= `Ink`) | `#0E1526` | body text | 15.3 on Paper, 14.1 on PaperShade, 13.0 on PaperHigh |
| `OnPaperMuted` | `#4E5360` | secondary text (ink thinned toward slate) | 6.5 on Paper, 5.9 on PaperShade, 5.5 on PaperHigh |
| `SealDeep` (primary) | `#9C3A24` | buttons, highlights, links | 5.8 on Paper, 5.3 on PaperShade, 4.9 on PaperHigh |
| `Paper` on `SealDeep` | — | text on primary buttons, the reader's highlight | 5.8 |
| `SealTint` | `#F2D5C6` | selected items (always with a 2 dp SealDeep border), active tab, tracks | Ink 13.1 on it, SealDeep 5.0 on it |
| `OutlineDeep` | `#5F6577` | borders, dividers | 4.9 on Paper, 4.1 on PaperHigh |
| `PaperDeep` | `#D9C8A9` | decorative dividers, disabled borders (never text) | — |
| `DangerDeep` (error) | `#A4291F` | errors | 6.0 on Paper, 5.1 on PaperHigh |
| `DangerTint` | `#F6D3CB` | error containers | Ink 13.1 on it |

The light scheme's snackbars are the logo's night blue (`Surface`) with paper
text (13.0) and a `Seal` action (5.6). The voice emblems keep their
night-blue disc in both schemes; on paper cards the disc itself stands out
(11.1 : 1).

### Material colour roles

`ui/theme/Theme.kt` maps **every** Material 3 colour role of both schemes
(`RReaderDarkColorScheme`, `RReaderLightColorScheme`) to these tokens, so no
component (cards, navigation bar, progress bars, sliders, sheets, snackbars)
falls back to Material's default purples and greys. `ThemeTest` fails if a
role of either scheme is off-palette, a text/surface pair drops below
4.5 : 1, or a surface would get the wrong content colour.

| Roles | Dark | Light |
|-------|------|-------|
| primary, secondary, tertiary | `Seal`, with `OnSeal` on it | `SealDeep`, with `Paper` on it |
| their *Fixed* variants (the same in both schemes, as Material intends) | `Seal`, with `OnSeal` on it | `Seal`, with `OnSeal` on it |
| primary/secondary/tertiary containers (selection, active tab, tracks) | `SealContainer`, with `OnInk` on it | `SealTint`, with `Ink` on it |
| background | `Ink` | `Paper` |
| surfaceDim, surfaceContainerLowest | `Ink` | `PaperHigh`, `Paper` |
| surface, surfaceContainerLow, surfaceContainer (navigation bar, sheets, menus) | `Surface` | `PaperShade` |
| surfaceContainerHigh/Highest (cards, dialogs, mini player) | `SurfaceHigh` | `PaperHigh` |
| surfaceBright | `SurfaceHigh` | `Paper` |
| surfaceVariant (legacy; screens use the container roles) | `SurfaceMid` | `PaperMid` |
| onBackground, onSurface / onSurfaceVariant | `OnInk` / `OnInkMuted` | `Ink` / `OnPaperMuted` |
| inverseSurface, inverseOnSurface, inversePrimary | `OnInk`, `Ink`, `SealDeep` | `Surface`, `Paper`, `Seal` |
| error, onError, errorContainer, onErrorContainer | `Danger`, `Ink`, `DangerContainer`, `OnInk` | `DangerDeep`, `Paper`, `DangerTint`, `Ink` |
| outline, outlineVariant, surfaceTint, scrim | `Outline`, `OutlineVariant`, `Outline`, black | `OutlineDeep`, `PaperDeep`, `OutlineDeep`, black |

### Theme

Ajustes offers **Escuro** (the default, today's look), **Claro** and **Igual
ao celular** (follows the phone's dark theme setting). The choice applies at
once to every screen; the status and navigation bar icons follow it (light
icons on the dark theme, dark icons on the light one). The launch screen
stays night blue in both.

## Typeface

**Atkinson Hyperlegible Next** (Braille Institute; SIL Open Font License
1.1), bundled as one variable font file
(`app/src/main/res/font/atkinson_hyperlegible_next.ttf`, weights 200–800; the
app uses 400, 500, 600 and 700). It was designed for readers with low vision:
similar letters stay easy to tell apart (I, l and 1; O and 0). It covers
Portuguese (ã, õ, ç, ê…) and typographic punctuation (— “ ” …). It is the
app's only typeface.

### Text size

Ajustes offers four text sizes for every screen, on top of the phone's own
font size: **Normal** (1×), **Grande** (1.15×), **Maior** (1.3×) and **Muito
grande** (1.5×). The theme multiplies the phone's font scale by the step
(`RReaderTheme`), so every size in sp grows with it, with the phone's own non-
linear scaling for large text; dialogs, sheets and menus apply it again in
their own windows (`ui/components/AppWindows.kt`). Screens scroll when their
content gets taller than the screen, and the player's cover gives way to the
controls when no room is left. The tab labels follow the size until the
longest no longer fits its tab on one line; then all four take the largest
size at which it fits, never smaller than at the phone's normal font size (in
Portuguese at the largest size on a 384 dp wide phone, about 1.14×). The
reader's A− and A+ apply on top of all of these.

## Type scale

Every Material type role is defined in `ui/theme/Type.kt`, in the app's
typeface, so components never fall back to Material's small defaults.
Sizes are in sp and scale with the system font setting.

| Roles | Size / line height | Weight |
|-------|--------------------|--------|
| displayLarge, displayMedium, displaySmall | 48/56, 44/52, 40/48 | Bold |
| headlineLarge | 34/42 | Bold |
| headlineMedium, headlineSmall | 30/38, 28/36 | SemiBold |
| titleLarge, titleMedium, titleSmall | 26/32, 22/28, 20/26 | SemiBold |
| bodyLarge, bodyMedium, bodySmall | 20/30, 18/26, 18/24 | Regular |
| labelLarge | 18/24 | SemiBold |
| labelMedium, labelSmall | 18/24, 16/22 | Medium |

Rules:

- Screens use theme roles only (`MaterialTheme.colorScheme`,
  `MaterialTheme.typography`); a new colour becomes a token in
  `ui/theme/Color.kt` first, in both schemes.
- Raised areas (cards, the mini player) use the container roles
  (`surfaceContainerHigh`), not the legacy `surfaceVariant`, so their text
  gets the full-strength colour.
- Every full screen draws on `ScreenSurface` (`ui/components/`), directly or
  through the app shell, so text and icons take the theme's content colour.
- State is never shown by colour alone (selected voice: border + "In use";
  a chosen option in Ajustes: filled radio button, border and check mark).
- Keep text on the background and surface tones; do not put body text on the
  primary colour.
- Type: body ≥ 18 sp, nothing below 16 sp, honours the system font scale and
  the listener's text size.
