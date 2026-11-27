package com.giovannimartins.rreader.catalog

import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long a catalogue download keeps trying (ADR 0020): a failure that may pass (no connection, Gutenberg's site and
 * its mirror down or busy) is tried again after [pause], until [deadline] after the first try; then the download fails
 * and says why, and the listener may try again.
 */
class DownloadRetryPolicy internal constructor(
    val deadline: Duration,
    val pause: Duration,
    val timeSource: TimeSource,
) {
    @Inject
    constructor() : this(DEADLINE, PAUSE, TimeSource.Monotonic)

    companion object {
        /** How long after the first try no new try starts; a try under way may end a little later. */
        val DEADLINE: Duration = 2.minutes

        /** The wait before trying again. */
        val PAUSE: Duration = 15.seconds
    }
}
