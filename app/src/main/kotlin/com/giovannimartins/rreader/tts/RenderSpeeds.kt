package com.giovannimartins.rreader.tts

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The speeds a voice that can speak faster or slower itself ([Voice.rendersAtSpeed]) renders at: the listening speed
 * rounded to the player's steps of [STEP], so a voice speaks at the speed the listener chose and the player does not
 * have to stretch its audio. Rendering at 1.25× also takes about a fifth less time than rendering at 1.0×, which is
 * what lets a slow neural voice keep up. Audio cached at another speed is still played, stretched by the player to
 * make up the difference.
 */
object RenderSpeeds {
    const val NORMAL = 1f
    private const val STEP = 0.25f
    private const val MIN = 0.75f
    private const val MAX = 2f

    /** Every speed audio can be rendered at. */
    val ALL: List<Float> = (0..((MAX - MIN) / STEP).roundToInt()).map { MIN + it * STEP }

    /** The speed to render at for listening at [listening]. */
    fun of(listening: Float): Float = ((listening / STEP).roundToInt() * STEP).coerceIn(MIN, MAX)

    /** The speeds to look for cached audio at, for listening at [listening]: the closest first. */
    fun lookupOrder(listening: Float): List<Float> {
        val target = of(listening)
        return ALL.sortedBy { abs(it - target) }
    }
}
