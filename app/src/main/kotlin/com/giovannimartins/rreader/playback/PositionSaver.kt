package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Saves the reading position without writing on every change: a forced save (segment change, pause, stop)
 * always writes, other saves (seeks) at most once per [throttle]. An unchanged position is never written
 * twice. Writes run one at a time in [scope], which outlives the player so the last save completes after a
 * release, and the newest position wins when writes queue up. [save] must be called from one thread.
 */
internal class PositionSaver(
    scope: CoroutineScope,
    private val elapsedMillis: () -> Long,
    private val write: suspend (String, ReadingPosition) -> Unit,
    private val throttle: Duration = DEFAULT_THROTTLE,
) {
    private val pending = Channel<Saved>(Channel.CONFLATED)
    private var lastSaved: Saved? = null
    private var lastWriteAt: Long? = null

    init {
        scope.launch {
            for (saved in pending) write(saved.bookId, saved.position)
        }
    }

    fun save(
        bookId: String,
        position: ReadingPosition,
        force: Boolean,
    ) {
        val saved = Saved(bookId, position)
        val now = elapsedMillis()
        val due = force || lastWriteAt.let { it == null || now - it >= throttle.inWholeMilliseconds }
        if (due && saved != lastSaved) {
            lastSaved = saved
            lastWriteAt = now
            pending.trySend(saved)
        }
    }

    /** Stops accepting saves; positions already accepted are still written. */
    fun close() {
        pending.close()
    }

    private data class Saved(
        val bookId: String,
        val position: ReadingPosition,
    )

    private companion object {
        val DEFAULT_THROTTLE = 5.seconds
    }
}
