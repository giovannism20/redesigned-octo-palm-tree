package com.giovannimartins.rreader.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.giovannimartins.rreader.R
import javax.inject.Inject

/** Builds the [ReaderPlayer] with an internal player configured for spoken-word listening. */
class ReaderPlayerFactory
    @Inject
    constructor(
        private val dependencies: ReaderPlayerDependencies,
    ) {
        /** A player on the main thread, where the media session service runs. */
        @OptIn(UnstableApi::class)
        fun create(context: Context): ReaderPlayer {
            val internal =
                ExoPlayer
                    .Builder(context)
                    .setLooper(Looper.getMainLooper())
                    // Pauses for phone calls and other apps, and resumes after transient interruptions.
                    .setAudioAttributes(SPEECH, true)
                    // Pauses when headphones are unplugged or a Bluetooth headset disconnects.
                    .setHandleAudioBecomingNoisy(true)
                    // Keeps the CPU awake while playing with the screen off.
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build()
            val labeler = ChapterLabeler { index -> context.getString(R.string.player_chapter_number, index + 1) }
            return ReaderPlayer(Looper.getMainLooper(), internal, dependencies, labeler, Clock.DEFAULT)
        }

        private companion object {
            val SPEECH: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build()
        }
    }
