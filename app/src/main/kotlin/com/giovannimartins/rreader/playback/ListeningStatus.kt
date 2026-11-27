package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.TextRange
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The segment being spoken: [range] of the normalized text of chapter [chapterIndex] of book [bookId]. */
data class SpokenSegment(
    val bookId: String,
    val chapterIndex: Int,
    val range: TextRange,
)

/**
 * The book open in the player, the [voice] that reads it (null while it has none), and whether the listener is
 * [listening]: playing, or waiting for audio to be prepared.
 */
data class ListeningSession(
    val bookId: String,
    val voice: Voice?,
    val listening: Boolean,
)

/**
 * Playback state that Media3's player state does not model, published by the [ReaderPlayer] for the UI and for work
 * that must not get in the way of listening: the segment being spoken, which the follow-along reader highlights, the
 * sleep timer, and the listening session, which pre-rendering stays out of.
 *
 * The playback service runs in the app's own process, so the player and the UI share this singleton instead
 * of encoding the state in session extras. It is read-only for the UI: commands still go through the
 * `MediaController` (see [ReaderCommands]). Values are published on the player's thread and can be read from
 * any thread.
 */
@Singleton
class ListeningStatus
    @Inject
    constructor() {
        private val segment = MutableStateFlow<SpokenSegment?>(null)

        private val timer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)

        private val openBook = MutableStateFlow<ListeningSession?>(null)

        /** The segment being spoken, or about to be after a seek; null while no book is open. */
        val spokenSegment: StateFlow<SpokenSegment?> = segment.asStateFlow()

        /** The sleep timer and, while it counts down, the listening time left. */
        val sleepTimer: StateFlow<SleepTimerState> = timer.asStateFlow()

        /** The open book, its voice and whether it is being listened to; null while no book is open. */
        val session: StateFlow<ListeningSession?> = openBook.asStateFlow()

        internal fun publishSegment(spoken: SpokenSegment?) {
            segment.value = spoken
        }

        internal fun publishSleepTimer(state: SleepTimerState) {
            timer.value = state
        }

        internal fun publishSession(session: ListeningSession?) {
            openBook.value = session
        }
    }
