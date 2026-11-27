package com.giovannimartins.rreader.ui.voices

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Plays voice samples on the Voices screen, one at a time. Used on the main thread while the screen is shown. */
interface VoiceSamplePlayer {
    /** Plays [uri], stopping a sample that was playing; [onEnded] runs when it ends or cannot be played. */
    fun play(
        uri: Uri,
        onEnded: () -> Unit,
    )

    fun stop()

    fun release()
}

/**
 * [VoiceSamplePlayer] on ExoPlayer, created on first use. It takes the audio focus like any media, so a book being
 * listened to pauses while a sample plays (the listener chose to hear the sample).
 */
class ExoVoiceSamplePlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : VoiceSamplePlayer {
        private var player: ExoPlayer? = null
        private var onEnded: (() -> Unit)? = null

        private val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) ended()
                }

                override fun onPlayerError(error: PlaybackException) = ended()
            }

        override fun play(
            uri: Uri,
            onEnded: () -> Unit,
        ) {
            val exo =
                player ?: ExoPlayer
                    .Builder(context)
                    .setAudioAttributes(SAMPLE, true)
                    .build()
                    .also {
                        it.addListener(listener)
                        player = it
                    }
            this.onEnded = onEnded
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.play()
        }

        override fun stop() {
            onEnded = null
            player?.stop()
        }

        override fun release() {
            onEnded = null
            player?.release()
            player = null
        }

        private fun ended() {
            onEnded?.invoke()
            onEnded = null
        }

        private companion object {
            val SAMPLE: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build()
        }
    }

@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceSampleModule {
    /** A player per screen: the view model releases it. */
    @Binds
    abstract fun samplePlayer(player: ExoVoiceSamplePlayer): VoiceSamplePlayer
}
