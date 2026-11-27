package com.giovannimartins.rreader.ui.components

import android.content.res.Resources
import androidx.annotation.PluralsRes
import com.giovannimartins.rreader.R
import kotlin.time.Duration

/** A time left rounded to the nearest minute for display; no hours and no minutes mean less than a minute. */
internal data class TimeLeft(
    val hours: Long,
    val minutes: Long,
) {
    /** The number the sentence agrees with: the hours when there are any, else the minutes, and 1 below that. */
    val quantity: Int
        get() {
            val count = if (hours > 0) hours else minutes.coerceAtLeast(1)
            return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    companion object {
        private const val SECONDS_PER_MINUTE = 60L
        private const val MINUTES_PER_HOUR = 60L

        /** [duration] rounded to the nearest minute, or null when no time is left. */
        fun of(duration: Duration): TimeLeft? =
            duration.takeIf { it.isPositive() }?.let {
                val minutes = (it.inWholeSeconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE
                TimeLeft(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
            }
    }
}

/**
 * [duration] as a time left the listener reads at a glance, such as "Faltam 3 h 20 min": the amount ("3 h 20
 * min", "45 min", "1 h", "less than 1 min") inside the sentence of [plural], which agrees with
 * [TimeLeft.quantity]. Null when no time is left.
 */
internal fun Resources.timeLeft(
    @PluralsRes plural: Int,
    duration: Duration,
): String? =
    TimeLeft.of(duration)?.let { parts ->
        val amount =
            when {
                parts.hours == 0L && parts.minutes == 0L -> getString(R.string.duration_under_a_minute)
                parts.hours == 0L -> getString(R.string.duration_minutes, parts.minutes)
                parts.minutes == 0L -> getString(R.string.duration_hours, parts.hours)
                else -> getString(R.string.duration_hours_minutes, parts.hours, parts.minutes)
            }
        getQuantityString(plural, parts.quantity, amount)
    }
