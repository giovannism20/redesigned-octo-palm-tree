package com.giovannimartins.rreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.settings.ReadingFont

/** One weight of the variable font file. */
private fun appFont(weight: FontWeight) =
    Font(
        resId = R.font.atkinson_hyperlegible_next,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/**
 * Atkinson Hyperlegible Next (SIL Open Font License 1.1), designed by the Braille Institute for readers with
 * low vision: similar letters stay easy to tell apart (I, l and 1; O and 0, whose slash the owner kept). The app's
 * typeface for everything; only the follow-along reader's text may use [LiterataFontFamily] instead.
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

/**
 * Literata (SIL Open Font License 1.1, TypeTogether), a serif book face, offered for the follow-along reader's text
 * only. One variable font file; the reader uses its regular weight at the font's own text optical size.
 */
internal val LiterataFontFamily =
    FontFamily(
        Font(
            resId = R.font.literata,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.Normal.weight)),
        ),
    )

/** The typeface of a reading font. */
internal val ReadingFont.fontFamily: FontFamily
    get() =
        when (this) {
            ReadingFont.ATKINSON -> AppFontFamily
            ReadingFont.LITERATA -> LiterataFontFamily
        }

/**
 * The follow-along reader's text in the listener's reading [font]: 22 sp with 34 sp lines, times the reader's own
 * [scale] (A− and A+). Sizes are in sp, so the app's text size and the phone's font size apply on top.
 */
@Composable
@ReadOnlyComposable
internal fun readingTextStyle(
    font: ReadingFont,
    scale: Float,
): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        fontFamily = font.fontFamily,
        fontSize = READING_FONT_SIZE * scale,
        lineHeight = READING_LINE_HEIGHT * scale,
    )

private val READING_FONT_SIZE = 22.sp
private val READING_LINE_HEIGHT = 34.sp
