package com.giovannimartins.rreader.ui.player

import androidx.annotation.StringRes
import com.giovannimartins.rreader.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Text shown by the player that does not come straight from resources. */
internal object PlayerFormat {
    /** Speeds offered by the speed selector. */
    val SPEEDS: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** A time such as `4:05` or `1:02:03`; negative times count as zero. */
    fun duration(millis: Long): String =
        millis.coerceAtLeast(0).milliseconds.toComponents { hours, minutes, seconds, _ ->
            if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
        }

    /** A speed with the locale's decimal separator and one or two decimals: `0,75`, `1,0`, `1,5`. */
    fun speed(
        speed: Float,
        locale: Locale,
    ): String = DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(locale)).format(speed)
}

/** What the player says about a [PlaybackProblem]. */
@StringRes
internal fun PlaybackProblem.messageRes(): Int =
    when (this) {
        PlaybackProblem.NO_VOICE -> R.string.player_error_no_voice
        PlaybackProblem.BOOK_UNAVAILABLE -> R.string.player_error_book
        PlaybackProblem.SPEECH_FAILED -> R.string.player_error_speech
        PlaybackProblem.OTHER -> R.string.player_error_other
    }
