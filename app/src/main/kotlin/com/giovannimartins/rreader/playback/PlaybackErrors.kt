package com.giovannimartins.rreader.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.giovannimartins.rreader.tts.SynthesisFailure

/**
 * Errors of the facade player. The codes are in Media3's custom range, so controllers such as the UI receive
 * them intact and map them to messages. Messages never contain book content (SR-8).
 */
@OptIn(UnstableApi::class)
object PlaybackErrors {
    /** No installed voice can read the book: the listener needs to install or choose one. */
    const val NO_VOICE = PlaybackException.CUSTOM_ERROR_CODE_BASE + 1

    /** The book is missing, not ready or its files cannot be read. */
    const val BOOK_UNAVAILABLE = PlaybackException.CUSTOM_ERROR_CODE_BASE + 2

    /** The speech engine failed to render a segment. Trying again may work. */
    const val SPEECH_FAILED = PlaybackException.CUSTOM_ERROR_CODE_BASE + 3

    fun noVoice(): PlaybackException = PlaybackException("No usable voice is installed", null, NO_VOICE)

    fun bookUnavailable(cause: Throwable? = null): PlaybackException =
        PlaybackException("The book cannot be read", cause, BOOK_UNAVAILABLE)

    fun speechFailed(reason: SynthesisFailure): PlaybackException =
        when (reason) {
            SynthesisFailure.ENGINE_UNAVAILABLE,
            SynthesisFailure.VOICE_UNAVAILABLE,
            -> PlaybackException("The voice is not available ($reason)", null, NO_VOICE)

            SynthesisFailure.RENDER_ERROR,
            SynthesisFailure.TIMEOUT,
            -> PlaybackException("The voice failed to speak ($reason)", null, SPEECH_FAILED)
        }
}
