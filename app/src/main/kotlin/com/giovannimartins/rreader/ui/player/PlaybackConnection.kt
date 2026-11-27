package com.giovannimartins.rreader.ui.player

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.playback.BookMediaItems
import com.giovannimartins.rreader.playback.PlaybackService
import com.giovannimartins.rreader.playback.ReaderCommand
import com.giovannimartins.rreader.playback.ReaderCommands
import com.giovannimartins.rreader.playback.SleepTimerSetting
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the UI to [PlaybackService] through a [MediaController] while the app is visible: observe it from
 * the activity's lifecycle. Commands sent before the connection is ready run as soon as it is; the position
 * is refreshed twice a second while listening.
 */
@Singleton
class PlaybackConnection
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PlaybackControls,
        DefaultLifecycleObserver {
        private val state = MutableStateFlow<NowPlaying?>(null)
        override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

        private val handler = Handler(Looper.getMainLooper())
        private val pending = ArrayDeque<(MediaController) -> Unit>()
        private var connecting: ListenableFuture<MediaController>? = null
        private var controller: MediaController? = null
        private var chapterTitles: List<String> = emptyList()

        private val events =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) chapterTitles = player.chapterTitles()
                    publish(player)
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) scheduleTick()
                }
            }

        private val disconnection =
            object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // The service went away, rather than the app leaving the screen: nothing is open anymore.
                    if (controller === this@PlaybackConnection.controller) {
                        disconnect()
                        state.value = null
                    }
                }
            }

        private val tick =
            Runnable {
                controller?.let(::publish)
                scheduleTick()
            }

        override fun onStart(owner: LifecycleOwner) = connect()

        override fun onStop(owner: LifecycleOwner) = disconnect()

        override fun play(bookId: String) =
            withController { player ->
                val openBook =
                    player.currentMediaItem
                        ?.mediaId
                        ?.let(BookMediaItems::parse)
                        ?.bookId
                if (openBook == bookId) {
                    makeReady(player)
                } else {
                    player.setMediaItem(MediaItem.Builder().setMediaId(bookId).build())
                    player.prepare()
                }
                player.play()
            }

        override fun resume() =
            withController { player ->
                makeReady(player)
                player.play()
            }

        override fun pause() = withController(Player::pause)

        override fun seekBack() = withController(Player::seekBack)

        override fun seekForward() = withController(Player::seekForward)

        override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

        override fun playChapter(index: Int) =
            withController { player ->
                player.seekTo(index, 0)
                makeReady(player)
                player.play()
            }

        override fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

        override fun readFrom(position: ReadingPosition) = send(ReaderCommand.ReadFrom(position))

        override fun setSleepTimer(setting: SleepTimerSetting) = send(ReaderCommand.SetSleepTimer(setting))

        override fun cancelSleepTimer() = send(ReaderCommand.CancelSleepTimer)

        /** Sends a custom [command]; the service may refuse it, for example when no book is open anymore. */
        private fun send(command: ReaderCommand) =
            withController { player ->
                val (sessionCommand, args) = ReaderCommands.encode(command)
                Futures.addCallback(
                    player.sendCustomCommand(sessionCommand, args),
                    CommandOutcome(sessionCommand.customAction),
                    ContextCompat.getMainExecutor(context),
                )
            }

        private fun connect() {
            if (connecting != null) return
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).setListener(disconnection).buildAsync()
            connecting = future
            future.addListener({
                if (connecting ===
                    future
                ) {
                    onConnected(future)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        private fun onConnected(future: ListenableFuture<MediaController>) {
            val connected =
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    Diagnostics.recordHandled("connect to playback", e)
                    null
                }
            if (connected == null) {
                connecting = null
                pending.clear()
            } else {
                controller = connected
                connected.addListener(events)
                chapterTitles = connected.chapterTitles()
                publish(connected)
                scheduleTick()
                while (pending.isNotEmpty()) pending.removeFirst().invoke(connected)
            }
        }

        private fun disconnect() {
            val future = connecting
            handler.removeCallbacks(tick)
            controller?.removeListener(events)
            connecting = null
            controller = null
            pending.clear()
            future?.let(MediaController::releaseFuture)
        }

        private fun withController(action: (MediaController) -> Unit) {
            val ready = controller
            if (ready != null) {
                action(ready)
            } else {
                pending.addLast(action)
                connect()
            }
        }

        private fun publish(player: Player) {
            state.value = player.toNowPlaying(chapterTitles)
        }

        private fun scheduleTick() {
            handler.removeCallbacks(tick)
            if (controller?.isPlaying == true) handler.postDelayed(tick, TICK_MS)
        }

        /** Prepares a stopped or failed player again, and goes back to the start of a finished book. */
        private fun makeReady(player: Player) {
            when (player.playbackState) {
                Player.STATE_IDLE -> player.prepare()
                Player.STATE_ENDED -> player.seekToDefaultPosition(0)
                else -> Unit
            }
        }

        /** Records custom commands the service refused or could not receive. */
        private class CommandOutcome(
            private val action: String,
        ) : FutureCallback<SessionResult> {
            override fun onSuccess(result: SessionResult) {
                val code = result.resultCode
                if (code != SessionResult.RESULT_SUCCESS) Diagnostics.recordRefused(action, code)
            }

            override fun onFailure(error: Throwable) = Diagnostics.recordHandled(action, error)
        }

        private companion object {
            const val TICK_MS = 500L
        }
    }
