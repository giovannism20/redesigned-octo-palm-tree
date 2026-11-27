package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.ui.MIN_CONTRAST
import com.giovannimartins.rreader.ui.contrastRatio
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Guards the design system in both colour schemes: brand colours only, readable pairs, and no small or foreign text
 * styles.
 */
class ThemeTest {
    private val schemes = mapOf("dark" to RReaderDarkColorScheme, "light" to RReaderLightColorScheme)

    private val brandPalette =
        listOf(
            Ink,
            Paper,
            Surface,
            SurfaceMid,
            SurfaceHigh,
            OnInkMuted,
            Seal,
            SealContainer,
            SealDeep,
            Outline,
            OutlineVariant,
            Danger,
            DangerContainer,
            PaperShade,
            PaperMid,
            PaperHigh,
            PaperDeep,
            OnPaperMuted,
            SealTint,
            OutlineDeep,
            DangerDeep,
            DangerTint,
            Color.Black,
        )

    @Test
    fun `every colour role of both schemes comes from the brand palette`() {
        val brand = brandPalette.map { it.value.toLong() }.toSet()
        val roles =
            ColorScheme::class.java.methods.filter {
                it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType
            }
        assertThat(roles.size).isAtLeast(MATERIAL_COLOR_ROLES)

        schemes.forEach { (name, scheme) ->
            val offBrand = roles.filter { it.invoke(scheme) as Long !in brand }.map { it.name.substringBefore('-') }
            assertWithMessage("$name roles not mapped to the brand palette").that(offBrand).isEmpty()
        }
    }

    @Test
    fun `the light scheme is ink on paper with a deep sealing wax`() {
        with(RReaderLightColorScheme) {
            assertThat(background).isEqualTo(Paper)
            assertThat(onBackground).isEqualTo(Ink)
            assertThat(onSurface).isEqualTo(Ink)
            assertThat(primary).isEqualTo(SealDeep)
        }
        assertThat(RReaderDarkColorScheme.background).isEqualTo(Ink)
    }

    @Test
    fun `text and icon colours meet WCAG AA on the surfaces they are used on, in both schemes`() {
        schemes.forEach { (scheme, colors) ->
            readablePairs(colors).forEach { (name, pair) ->
                assertWithMessage("$scheme: $name")
                    .that(contrastRatio(pair.first, pair.second))
                    .isAtLeast(MIN_CONTRAST)
            }
        }
    }

    @Test
    fun `voice emblems stand out from their night-blue disc, and the disc from the light cards`() {
        VoiceEmblemColors.forEach { color ->
            assertThat(contrastRatio(color, Surface)).isAtLeast(MIN_GRAPHIC_CONTRAST)
            assertThat(contrastRatio(color, SurfaceHigh)).isAtLeast(MIN_GRAPHIC_CONTRAST)
            assertThat(color.value.toLong()).isIn(brandPalette.map { it.value.toLong() })
        }
        with(RReaderLightColorScheme) {
            listOf(surfaceContainerHigh, primaryContainer, background).forEach { card ->
                assertThat(contrastRatio(Surface, card)).isAtLeast(MIN_GRAPHIC_CONTRAST)
            }
        }
    }

    @Test
    fun `components get the intended content colour on every surface, in both schemes`() {
        schemes.forEach { (name, scheme) ->
            with(scheme) {
                val plainSurfaces =
                    listOf(
                        background,
                        surface,
                        surfaceDim,
                        surfaceBright,
                        surfaceContainerLowest,
                        surfaceContainerLow,
                        surfaceContainer,
                        surfaceContainerHigh,
                        surfaceContainerHighest,
                    )
                plainSurfaces.forEach { assertWithMessage(name).that(contentColorFor(it)).isEqualTo(onSurface) }
                // surfaceVariant must differ from the container tones, or their text would get the muted colour.
                assertWithMessage(name).that(contentColorFor(surfaceVariant)).isEqualTo(onSurfaceVariant)
                assertWithMessage(name).that(surfaceVariant).isNotIn(plainSurfaces)
                assertWithMessage(name).that(contentColorFor(primary)).isEqualTo(onPrimary)
                assertWithMessage(name).that(contentColorFor(primaryContainer)).isEqualTo(onPrimaryContainer)
                assertWithMessage(name).that(contentColorFor(secondaryContainer)).isEqualTo(onSecondaryContainer)
                assertWithMessage(name).that(contentColorFor(errorContainer)).isEqualTo(onErrorContainer)
                assertWithMessage(name).that(contentColorFor(inverseSurface)).isEqualTo(inverseOnSurface)
            }
        }
    }

    @Test
    fun `every text style uses the app typeface and is at least 16 sp`() {
        val styles =
            with(RReaderTypography) {
                listOf(
                    displayLarge,
                    displayMedium,
                    displaySmall,
                    headlineLarge,
                    headlineMedium,
                    headlineSmall,
                    titleLarge,
                    titleMedium,
                    titleSmall,
                    bodyLarge,
                    bodyMedium,
                    bodySmall,
                    labelLarge,
                    labelMedium,
                    labelSmall,
                )
            }

        styles.forEach { style ->
            assertThat(style.fontFamily).isEqualTo(AppFontFamily)
            assertThat(style.fontSize.value).isAtLeast(MIN_TEXT_SP)
            assertThat(style.lineHeight).isNotEqualTo(TextUnit.Unspecified)
        }
        assertThat(RReaderTypography.bodyMedium.fontSize.value).isAtLeast(MIN_BODY_SP)
        assertThat(RReaderTypography.bodySmall.fontSize.value).isAtLeast(MIN_BODY_SP)
    }

    @Test
    fun `only the reading font may be Literata`() {
        assertThat(ReadingFont.ATKINSON.fontFamily).isEqualTo(AppFontFamily)
        assertThat(ReadingFont.LITERATA.fontFamily).isEqualTo(LiterataFontFamily)
    }

    /** Every text or icon colour of [colors] with the surface it is drawn on. */
    private fun readablePairs(colors: ColorScheme): List<Pair<String, Pair<Color, Color>>> =
        with(colors) {
            val surfaces =
                mapOf(
                    "background" to background,
                    "surface" to surface,
                    "surfaceVariant" to surfaceVariant,
                    "surfaceContainerLowest" to surfaceContainerLowest,
                    "surfaceContainerLow" to surfaceContainerLow,
                    "surfaceContainer" to surfaceContainer,
                    "surfaceContainerHigh" to surfaceContainerHigh,
                    "surfaceContainerHighest" to surfaceContainerHighest,
                    "surfaceBright" to surfaceBright,
                    "surfaceDim" to surfaceDim,
                )
            val onSurfaces =
                surfaces.flatMap { (name, color) ->
                    listOf(
                        "onSurface on $name" to (onSurface to color),
                        "onSurfaceVariant on $name" to (onSurfaceVariant to color),
                        "primary on $name" to (primary to color),
                        "secondary on $name" to (secondary to color),
                        "error on $name" to (error to color),
                    )
                }
            onSurfaces +
                listOf(
                    "onBackground" to (onBackground to background),
                    "onPrimary" to (onPrimary to primary),
                    "onPrimaryContainer" to (onPrimaryContainer to primaryContainer),
                    "primary on primaryContainer" to (primary to primaryContainer),
                    "onSecondary" to (onSecondary to secondary),
                    "onSecondaryContainer" to (onSecondaryContainer to secondaryContainer),
                    "onTertiary" to (onTertiary to tertiary),
                    "onTertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                    "inverseOnSurface" to (inverseOnSurface to inverseSurface),
                    "inversePrimary on inverseSurface" to (inversePrimary to inverseSurface),
                    "onError" to (onError to error),
                    "onErrorContainer" to (onErrorContainer to errorContainer),
                    "onPrimaryFixed" to (onPrimaryFixed to primaryFixed),
                    "onSecondaryFixed" to (onSecondaryFixed to secondaryFixed),
                    "onTertiaryFixed" to (onTertiaryFixed to tertiaryFixed),
                    "onCover" to (OnCover to CoverColors.minBy { contrastRatio(OnCover, it) }),
                )
        }

    private companion object {
        /** Colour roles of material3 1.5. */
        const val MATERIAL_COLOR_ROLES = 48
        const val MIN_TEXT_SP = 16f

        /** WCAG AA for graphical objects such as the voice emblems. */
        const val MIN_GRAPHIC_CONTRAST = 3f
        const val MIN_BODY_SP = 18f
    }
}
