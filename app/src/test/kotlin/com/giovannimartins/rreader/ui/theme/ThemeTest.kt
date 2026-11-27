package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.giovannimartins.rreader.ui.MIN_CONTRAST
import com.giovannimartins.rreader.ui.contrastRatio
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Guards the design system: brand colours only, readable pairs, and no small or foreign text styles. */
class ThemeTest {
    private val brandPalette =
        listOf(
            Ink,
            Surface,
            SurfaceMid,
            SurfaceHigh,
            OnInk,
            OnInkMuted,
            Seal,
            OnSeal,
            SealContainer,
            SealDeep,
            Outline,
            OutlineVariant,
            Danger,
            DangerContainer,
            Color.Black,
        )

    @Test
    fun `every colour role comes from the brand palette`() {
        val brand = brandPalette.map { it.value.toLong() }.toSet()
        val roles =
            ColorScheme::class.java.methods.filter {
                it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType
            }

        val offBrand =
            roles
                .filter { it.invoke(RReaderColorScheme) as Long !in brand }
                .map { it.name.substringBefore('-') }

        assertThat(roles.size).isAtLeast(MATERIAL_COLOR_ROLES)
        assertWithMessage("roles not mapped to the brand palette").that(offBrand).isEmpty()
    }

    @Test
    fun `text and icon colours meet WCAG AA on the surfaces they are used on`() {
        val pairs =
            with(RReaderColorScheme) {
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

        pairs.forEach { (name, colors) ->
            assertWithMessage(name).that(contrastRatio(colors.first, colors.second)).isAtLeast(MIN_CONTRAST)
        }
    }

    @Test
    fun `components get the intended content colour on every surface`() {
        with(RReaderColorScheme) {
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
            plainSurfaces.forEach { assertThat(contentColorFor(it)).isEqualTo(onSurface) }
            assertThat(contentColorFor(surfaceVariant)).isEqualTo(onSurfaceVariant)
            assertThat(contentColorFor(primary)).isEqualTo(onPrimary)
            assertThat(contentColorFor(primaryContainer)).isEqualTo(onPrimaryContainer)
            assertThat(contentColorFor(secondaryContainer)).isEqualTo(onSecondaryContainer)
            assertThat(contentColorFor(errorContainer)).isEqualTo(onErrorContainer)
            assertThat(contentColorFor(inverseSurface)).isEqualTo(inverseOnSurface)
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

    private companion object {
        /** Colour roles of material3 1.5. */
        const val MATERIAL_COLOR_ROLES = 48
        const val MIN_TEXT_SP = 16f
        const val MIN_BODY_SP = 18f
    }
}
