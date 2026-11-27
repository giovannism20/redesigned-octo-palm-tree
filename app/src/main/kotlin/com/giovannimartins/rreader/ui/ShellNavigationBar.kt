package com.giovannimartins.rreader.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density

/**
 * The bottom navigation bar: every tab with its icon and its label on one line. The labels follow the listener's text
 * size until the longest one would no longer fit its tab; then all of them take the largest size at which it does,
 * never smaller than at the phone's normal font size.
 */
@Composable
internal fun ShellNavigationBar(
    destination: TopDestination,
    onDestinationSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = TopDestination.entries.map { stringResource(it.labelRes) }
    NavigationBar(modifier = modifier) {
        TopDestination.entries.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = destination == item,
                onClick = { onDestinationSelect(item) },
                icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                label = { TabLabel(text = labels[index], labels = labels) },
            )
        }
    }
}

@Composable
private fun TabLabel(
    text: String,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val styles = with(MaterialTheme.typography) { listOf(labelMedium, labelSmall) }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth * FIT_MARGIN else Float.POSITIVE_INFINITY
        val (style, fontScale) =
            remember(labels, available, density, styles) { fitLabels(measurer, labels, styles, available, density) }
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Text(text = text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The first of [styles] whose widest label fits [available] pixels at some font scale from the current one down to
 * the phone's normal size (1), with that scale. Text width grows in proportion to the font size, so the widest label
 * is measured once per style and the scales are worked out from it. When nothing fits (a very narrow screen), the last
 * style at the normal size, which ends in an ellipsis.
 */
private fun fitLabels(
    measurer: TextMeasurer,
    labels: List<String>,
    styles: List<TextStyle>,
    available: Float,
    density: Density,
): Pair<TextStyle, Float> {
    val floor = minOf(density.fontScale, 1f)
    styles.forEach { style ->
        val widest =
            labels.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1, density = density).size.width }
        val sizeNow = density.fontSizePx(style)
        val widthAt = { scale: Float -> widest * Density(density.density, scale).fontSizePx(style) / sizeNow }
        val scale = largestFittingScale(density.fontScale, floor, available, widthAt)
        if (widthAt(scale) <= available) return style to scale
    }
    return styles.last() to floor
}

private fun Density.fontSizePx(style: TextStyle): Float = style.fontSize.toPx()

/**
 * The largest font scale from [current] down to [floor], in steps of 0.01, at which [widthAt] is at most [available];
 * [floor] when none is.
 */
internal fun largestFittingScale(
    current: Float,
    floor: Float,
    available: Float,
    widthAt: (Float) -> Float,
): Float {
    var scale = current
    while (scale > floor && widthAt(scale) > available) scale = maxOf(floor, scale - SCALE_STEP)
    return scale
}

private const val SCALE_STEP = 0.01f

/** Room left for rounding: a label may use 98 % of its tab's width. */
private const val FIT_MARGIN = 0.98f
