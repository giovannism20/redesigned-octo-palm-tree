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

## App palette (dark only)

Derived from the logo. Every text pair meets WCAG AA; the accent is a
slightly lighter sealing wax so it reaches AA on every background.

| Token (Compose) | Hex | Use | Contrast |
|-----------------|-----|-----|----------|
| `Ink` (background) | `#0E1526` | screen background, window background | — |
| `Surface` | `#18233F` | cards, sheets (the logo's night blue) | — |
| `SurfaceHigh` | `#22304F` | raised surfaces: cards, dialogs, mini player | — |
| `OnInk` | `#F5EAD6` | body text | 15.3 on Ink, 13.0 on Surface |
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
| `SealDeep` | `#B4442B` | actions on the paper-coloured inverse surface (snackbars) | 4.6 on OnInk |
| `DangerContainer` | `#5C2020` | error containers | OnInk 10.5 on it |
| `OutlineVariant` | `#3A4766` | decorative dividers, disabled borders (never text) | — |
| `CoverColors` / `OnCover` | five muted tones / white | generated covers of books without an image | ≥ 4.5 |

### Material colour roles

`ui/theme/Theme.kt` maps **every** Material 3 colour role to these tokens, so
no component (cards, navigation bar, progress bars, sliders, sheets,
snackbars) falls back to Material's default purples and greys. `ThemeTest`
fails if a role is off-palette or a text/surface pair drops below 4.5 : 1.

| Roles | Token |
|-------|-------|
| primary, secondary, tertiary (and their *Fixed* variants) | `Seal`, with `OnSeal` on it |
| primary/secondary/tertiary containers (selection, active tab, tracks) | `SealContainer`, with `OnInk` on it |
| background, surfaceDim, surfaceContainerLowest | `Ink` |
| surface, surfaceContainerLow, surfaceContainer (navigation bar, sheets, menus) | `Surface` |
| surfaceContainerHigh/Highest, surfaceBright (cards, dialogs, mini player) | `SurfaceHigh` |
| surfaceVariant (legacy; screens use the container roles) | `SurfaceMid` |
| onBackground, onSurface / onSurfaceVariant | `OnInk` / `OnInkMuted` |
| inverseSurface, inverseOnSurface, inversePrimary | `OnInk`, `Ink`, `SealDeep` |
| error, onError, errorContainer, onErrorContainer | `Danger`, `Ink`, `DangerContainer`, `OnInk` |
| outline, outlineVariant, surfaceTint, scrim | `Outline`, `OutlineVariant`, `Outline`, black |

## Typeface

**Atkinson Hyperlegible Next** (Braille Institute; SIL Open Font License
1.1), bundled as one variable font file
(`app/src/main/res/font/atkinson_hyperlegible_next.ttf`, weights 200–800; the
app uses 400, 500, 600 and 700). It was designed for readers with low vision:
similar letters stay easy to tell apart (I, l and 1; O and 0). It covers
Portuguese (ã, õ, ç, ê…) and typographic punctuation (— “ ” …). It is the
app's only typeface.

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
  `ui/theme/Color.kt` first.
- Raised areas (cards, the mini player) use the container roles
  (`surfaceContainerHigh`), not the legacy `surfaceVariant`, so their text
  gets the full-strength colour.
- Every full screen draws on `ScreenSurface` (`ui/components/`), directly or
  through the app shell, so text and icons take the theme's content colour.
- State is never shown by colour alone (selected voice: border + "In use").
- Keep text on `Ink`/`Surface`; do not put body text on `Seal`.
- Type: body ≥ 18 sp, nothing below 16 sp, honours the system font scale.
