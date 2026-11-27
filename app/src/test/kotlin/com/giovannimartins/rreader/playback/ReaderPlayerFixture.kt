package com.giovannimartins.rreader.playback

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import java.time.Clock

/**
 * A [ReaderPlayer] over a real internal ExoPlayer on an auto-advancing [FakeClock], rendering with [engine]
 * into an audio cache in [cacheDir] and reading [content]. Use it under Robolectric and release [player].
 */
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@OptIn(UnstableApi::class)
internal class ReaderPlayerFixture(
    cacheDir: File,
    val content: FakePlaybackContent,
    val engine: FakeSpeechEngine = FakeSpeechEngine(),
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val clock = FakeClock(true)
    val status = ListeningStatus()
    val internal: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setClock(clock)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .build()
    val player: ReaderPlayer

    init {
        val io = UnconfinedTestDispatcher()
        val rates = SpeakingRateTracker()
        val renderer = SegmentRenderer(engine, AudioCache(cacheDir), rates, Clock.systemUTC(), io)
        val dependencies =
            ReaderPlayerDependencies(
                content = content,
                renderer = renderer,
                planner = SegmentPlanner(),
                speech = SpeechNormalizer(),
                rates = rates,
                status = status,
                saveScope = CoroutineScope(io),
                cpu = io,
            )
        player =
            ReaderPlayer(
                Looper.getMainLooper(),
                internal,
                dependencies,
                { index -> "Chapter ${index + 1}" },
                SystemClock::elapsedRealtime,
            )
    }

    /** Opens the book of [content] and prepares the player, without starting playback. */
    fun open() {
        player.setMediaItem(MediaItem.Builder().setMediaId(BOOK_ID).build())
        player.prepare()
    }
}
