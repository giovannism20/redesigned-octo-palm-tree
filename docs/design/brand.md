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
| `SurfaceHigh` | `#22304F` | raised surfaces, surface variant | — |
| `OnInk` | `#F5EAD6` | body text | 15.3 on Ink, 13.0 on Surface |
| `OnInkMuted` | `#C8BEAD` | secondary text | 9.9 on Ink, 8.5 on Surface, 7.1 on SurfaceHigh |
| `Seal` (primary) | `#EE7A5F` | buttons, highlights, links | 6.6 on Ink, 5.6 on Surface |
| `OnSeal` | `#0E1526` | text on primary buttons | 6.6 on Seal |
| `SealContainer` | `#4A2B2A` | selected items (always with a 2 dp Seal border) | OnInk 10.6 on it |
| `Outline` | `#8792AE` | borders, dividers | 5.0 on Surface |
| `Danger` (error) | `#FFB4A9` | errors | 9.1 on Surface |

Rules:

- State is never shown by colour alone (selected voice: border + "In use").
- Keep text on `Ink`/`Surface`; do not put body text on `Seal`.
- Type scale: body ≥ 18 sp, honours the system font scale
  (`ui/theme/Type.kt`).
