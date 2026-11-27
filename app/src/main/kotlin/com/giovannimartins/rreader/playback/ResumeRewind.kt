package com.giovannimartins.rreader.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The small rewind when listening resumes after a long pause: the listener hears the end of what they heard
 * before stopping again, which helps after a nap or a long phone call. Short pauses resume exactly where they
 * stopped.
 */
internal object ResumeRewind {
    /** Pauses longer than this rewind a little when listening resumes. */
    val LONG_PAUSE: Duration = 5.minutes

    /** At most this much text, in media time at 1.0×, is heard again. */
    val MAX_REWIND: Duration = 10.seconds

    fun isLong(pause: Duration): Boolean = pause > LONG_PAUSE

    /**
     * Where listening resumes after a long pause at [paused]: one segment earlier ([previousSegmentStart], the
     * start of the segment before the one that holds [paused]) or [maxChars] characters earlier, whichever is
     * the smaller rewind. Playback then starts, as always, at the start of the segment that holds the result.
     * Without a previous segment, at the start of the book, it is [paused] itself.
     */
    fun target(
        paused: ReadingPosition,
        previousSegmentStart: ReadingPosition?,
        maxChars: Int,
        chapterLengths: List<Int>,
    ): ReadingPosition =
        previousSegmentStart?.let { maxOf(it, ReadingEstimates.shift(paused, -maxChars, chapterLengths)) } ?: paused
}

/**
 * Remembers when listening stopped (a pause, a stop, a failure or an interruption such as a phone call), so
 * the player can tell whether it resumes after a long pause ([ResumeRewind]). Measured on [clock], which keeps
 * counting while the phone sleeps.
 */
@OptIn(UnstableApi::class)
internal class PauseClock(
    private val clock: Clock,
) {
    private var stoppedAt: Long? = null

    /** Listening stopped now, unless it had already stopped. */
    fun stopped() {
        if (stoppedAt == null) stoppedAt = clock.elapsedRealtime()
    }

    /** The listener moved somewhere else (a seek, another book): the next resume does not follow a pause. */
    fun forget() {
        stoppedAt = null
    }

    /** Whether listening, resuming now, had stopped for a long pause; forgets the pause either way. */
    fun resumesAfterLongPause(): Boolean {
        val pause = stoppedAt?.let { (clock.elapsedRealtime() - it).milliseconds }
        stoppedAt = null
        return pause != null && ResumeRewind.isLong(pause)
    }
}
