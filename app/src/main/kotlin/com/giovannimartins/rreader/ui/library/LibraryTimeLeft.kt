package com.giovannimartins.rreader.ui.library

import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.textkit.estimate.ReadingEstimates
import com.giovannimartins.rreader.tts.SpeakingRates
import com.giovannimartins.rreader.tts.VoiceIds
import kotlin.time.Duration

/** Listening time left in the books of the library, estimated as the player estimates it (ADR 0006). */
internal object LibraryTimeLeft {
    /**
     * Time left in [book] at the listener's speed, with the speaking rate of the voice that reads it: the book's
     * own voice, else the default voice, else the voice that read last (the player picks the same voice in the
     * usual case of a single installed Portuguese voice, and "the phone's voice" names no voice to look up). Null
     * for books that are not ready or are finished.
     */
    fun of(
        book: LibraryBook,
        settings: AppSettings,
        rates: SpeakingRates,
    ): Duration? =
        book.takeIf { it.status == BookStatus.READY }?.let {
            val remaining = (it.totalChars - it.charsRead).coerceAtLeast(0)
            val chosen = it.voiceId ?: settings.defaultVoiceId
            val voiceId = chosen?.takeIf { id -> id != VoiceIds.PHONE } ?: rates.lastVoiceId
            ReadingEstimates
                .remainingDuration(remaining, rates.rateFor(voiceId), settings.playbackSpeed)
                .takeIf(Duration::isPositive)
        }
}
