package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.giovannimartins.rreader.R

/** One weight of the variable font file. */
private fun appFont(weight: FontWeight) =
    Font(
        resId = R.font.atkinson_hyperlegible_next,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/**
 * Atkinson Hyperlegible Next (SIL Open Font License 1.1), designed by the Braille Institute for readers with
 * low vision: similar letters stay easy to tell apart (I, l and 1; O and 0). The app's only typeface.
 */
internal val AppFontFamily =
    FontFamily(
        appFont(FontWeight.Normal),
        appFont(FontWeight.Medium),
        appFont(FontWeight.SemiBold),
        appFont(FontWeight.Bold),
    )

/** A text style in the app's typeface. */
private fun style(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontFamily = AppFontFamily, fontSize = size, lineHeight = lineHeight, fontWeight = weight)

/**
 * The whole Material type scale in the app's typeface. Every role is defined, so components never fall back
 * to Material's small defaults: nothing is smaller than 16 sp and body text starts at 18 sp. Sizes scale with
 * the system font setting.
 */
internal val RReaderTypography =
    Typography(
        displayLarge = style(size = 48.sp, lineHeight = 56.sp, weight = FontWeight.Bold),
        displayMedium = style(size = 44.sp, lineHeight = 52.sp, weight = FontWeight.Bold),
        displaySmall = style(size = 40.sp, lineHeight = 48.sp, weight = FontWeight.Bold),
        headlineLarge = style(size = 34.sp, lineHeight = 42.sp, weight = FontWeight.Bold),
        headlineMedium = style(size = 30.sp, lineHeight = 38.sp, weight = FontWeight.SemiBold),
        headlineSmall = style(size = 28.sp, lineHeight = 36.sp, weight = FontWeight.SemiBold),
        titleLarge = style(size = 26.sp, lineHeight = 32.sp, weight = FontWeight.SemiBold),
        titleMedium = style(size = 22.sp, lineHeight = 28.sp, weight = FontWeight.SemiBold),
        titleSmall = style(size = 20.sp, lineHeight = 26.sp, weight = FontWeight.SemiBold),
        bodyLarge = style(size = 20.sp, lineHeight = 30.sp),
        bodyMedium = style(size = 18.sp, lineHeight = 26.sp),
        bodySmall = style(size = 18.sp, lineHeight = 24.sp),
        labelLarge = style(size = 18.sp, lineHeight = 24.sp, weight = FontWeight.SemiBold),
        labelMedium = style(size = 18.sp, lineHeight = 24.sp, weight = FontWeight.Medium),
        labelSmall = style(size = 16.sp, lineHeight = 22.sp, weight = FontWeight.Medium),
    )
