package com.giovannimartins.rreader.ui.reader

import com.giovannimartins.rreader.data.settings.AppSettings

/** The text sizes A− and A+ step through, as multipliers on top of the system font scale. */
internal object ReaderTextScale {
    /** About 15 % apart, so every step is visible; within [AppSettings.TEXT_SCALE_RANGE]. */
    val STEPS: List<Float> = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f)

    /** Scales closer than this count as the same step, whatever rounding a stored value went through. */
    private const val TOLERANCE = 0.01f

    /** The next larger size after [scale], or null at the largest. */
    fun larger(scale: Float): Float? = STEPS.firstOrNull { it > scale + TOLERANCE }

    /** The next smaller size before [scale], or null at the smallest. */
    fun smaller(scale: Float): Float? = STEPS.lastOrNull { it < scale - TOLERANCE }
}
