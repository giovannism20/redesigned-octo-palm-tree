package com.giovannimartins.rreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertWithMessage

/** WCAG AA contrast for normal text. */
internal const val MIN_CONTRAST = 4.5f

/**
 * Asserts that every text on screen is drawn in a colour readable on [background]. Catches text that falls
 * back to the default black content colour when a screen is drawn without the shared screen surface.
 * Use it on states whose text sits on the screen background or on cards of a similar tone. Text drawn in one of
 * the colours of [containers] sits on the matching container instead (filled buttons: their text colour to their
 * container colour) and is checked against it.
 */
internal fun SemanticsNodeInteractionsProvider.assertAllTextReadableOn(
    background: Color,
    containers: Map<Color, Color> = emptyMap(),
) {
    val nodes =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
    assertWithMessage("text on screen").that(nodes).isNotEmpty()
    nodes.forEach { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        layouts.forEach { layout ->
            val color = layout.layoutInput.style.color
            assertWithMessage("colour of \"${layout.layoutInput.text}\"").that(color).isNotEqualTo(Color.Unspecified)
            val behind = containers[color] ?: background
            assertWithMessage("contrast of \"${layout.layoutInput.text}\" on the background")
                .that(contrastRatio(color.compositeOver(behind), behind))
                .isAtLeast(MIN_CONTRAST)
        }
    }
}

/** The WCAG contrast ratio between two opaque colours, from 1 to 21. */
internal fun contrastRatio(
    first: Color,
    second: Color,
): Float {
    val lighter = maxOf(first.luminance(), second.luminance()) + 0.05f
    val darker = minOf(first.luminance(), second.luminance()) + 0.05f
    return lighter / darker
}
