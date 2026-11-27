package com.giovannimartins.rreader.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** How far the player must be pulled down to minimize it when let go slowly. */
private val MINIMIZE_DISTANCE = 120.dp

/** How fast a downward flick must be (per second) to minimize the player however short it was. */
private val MINIMIZE_VELOCITY = 1000.dp

/**
 * Swiping down minimizes the player, like its minimize button. The player follows the finger once its
 * content is at the top (a long, scrolled player scrolls back first); letting go far or fast enough minimizes
 * it, otherwise it springs back. Attach it with `Modifier.nestedScroll` around the scrolling content and move
 * the content by [offset].
 */
@Stable
internal class SwipeToMinimizeState(
    private val minimizeDistancePx: Float,
    private val minimizeVelocityPx: Float,
    private val onMinimize: () -> Unit,
) : NestedScrollConnection {
    /** How far the player is pulled down, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Set once the swipe has minimized the player, so the end of the same gesture does not do it again. */
    private var minimized = false

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // Pulled down and now dragged up: the player goes back up before the content scrolls.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || offset <= 0f) return Offset.Zero
        val used = maxOf(available.y, -offset)
        offset += used
        return Offset(0f, used)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // A downward drag the content could not use (it is at its top) pulls the player down.
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        offset += available.y
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset <= 0f) return Velocity.Zero
        settle(available.y)
        return available
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        // In case the drag ended without a pre-fling, never leave the player half pulled down.
        if (offset > 0f) settle(available.y)
        return Velocity.Zero
    }

    private suspend fun settle(velocity: Float) {
        if (minimized) return
        if (offset > minimizeDistancePx || velocity > minimizeVelocityPx) {
            minimized = true
            onMinimize()
        } else {
            animate(initialValue = offset, targetValue = 0f) { value, _ -> offset = value }
        }
    }
}

/** A [SwipeToMinimizeState] that survives recompositions and always calls the latest [onMinimize]. */
@Composable
internal fun rememberSwipeToMinimize(onMinimize: () -> Unit): SwipeToMinimizeState {
    val currentOnMinimize by rememberUpdatedState(onMinimize)
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            SwipeToMinimizeState(
                minimizeDistancePx = MINIMIZE_DISTANCE.toPx(),
                minimizeVelocityPx = MINIMIZE_VELOCITY.toPx(),
                onMinimize = { currentOnMinimize() },
            )
        }
    }
}
