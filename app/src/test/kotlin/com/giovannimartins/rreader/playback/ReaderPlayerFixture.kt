package com.giovannimartins.rreader.playback

import android.content.Context
import android.os.Looper
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
import com.giovannimartins.rreader.tts.InMemorySpeakingRateStore
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A [ReaderPlayer] over a real internal ExoPlayer, rendering with [engine] into an audio cache in [cacheDir]
 * and reading [content]. Use it under Robolectric and release [player].
 *
 * The internal player runs on its own auto-advancing fake clock. The facade's [clock] (sleep timer, pause
 * lengths, save throttling) is a separate fake clock that only moves when a test advances it, so timers and
 * long pauses are deterministic while the audio plays on. The sleep timer fades over a short [sleepFade].
 */
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@OptIn(UnstableApi::class)
internal class ReaderPlayerFixture(
    cacheDir: File,
    val content: FakePlaybackContent,
    val engine: FakeSpeechEngine = FakeSpeechEngine(),
    sleepFade: Duration = 1.seconds,
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val clock = FakeClock(false)
    val status = ListeningStatus()

    /** The books asked to be rendered ahead while charging. */
    val prerendered = mutableListOf<String>()
    val internal: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setClock(FakeClock(true))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .build()
    val player: ReaderPlayer

    init {
        val io = UnconfinedTestDispatcher()
        val rates = SpeakingRateTracker(InMemorySpeakingRateStore(), CoroutineScope(io))
        val renderer = SegmentRenderer(engine, AudioCache(cacheDir), rates, Clock.systemUTC(), io)
        val dependencies =
            ReaderPlayerDependencies(
                content = content,
                renderer = renderer,
                planner = SegmentPlanner(),
                speech = SpeechNormalizer(),
                rates = rates,
                status = status,
                prerender = PrerenderJobs { bookId, _ -> prerendered += bookId },
                saveScope = CoroutineScope(io),
                cpu = io,
            )
        player =
            ReaderPlayer(
                Looper.getMainLooper(),
                internal,
                dependencies,
                { index -> "Chapter ${index + 1}" },
                clock,
                sleepFade,
            )
    }

    /** Opens the book of [content] and prepares the player, without starting playback. */
    fun open() {
        player.setMediaItem(MediaItem.Builder().setMediaId(BOOK_ID).build())
        player.prepare()
    }
}
