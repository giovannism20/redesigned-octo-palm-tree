package com.giovannimartins.rreader.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.giovannimartins.rreader.MainActivity
import com.giovannimartins.rreader.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/**
 * Runs the [ReaderPlayer] in a `mediaPlayback` foreground service with a media session, so listening goes on
 * with the screen locked and is controlled from the notification, the lock screen and headsets (ADR 0003).
 *
 * Android 17 lets apps play audio in the background only from a foreground service with while-in-use
 * capabilities (ADR 0014). Playback starts from a tap in the app while it is visible, which is when Media3
 * promotes this service to the foreground; it stays there through pauses and interruptions (Media3 keeps it
 * for ten minutes after a pause) and the service stops when the book ends or the app is swiped away while
 * paused. No media button receiver is declared, so a headset cannot start playback from the background.
 *
 * The service is exported, so any app can send it start intents. Media3 handles media buttons and notification
 * actions from such intents: it finds the session by the URI the intent carries, derived from the session id,
 * and otherwise asks [onGetSession] with an untrusted caller. The session therefore gets a random id, known only
 * to the notification's own pending intents, and [onGetSession] answers only trusted controllers (SR-7).
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playerFactory: ReaderPlayerFactory

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerFactory.create(this)
        player.addListener(StopAtEndOfBook(player))
        session =
            MediaSession
                .Builder(this, player)
                .setId(UUID.randomUUID().toString())
                .setSessionActivity(openPlayerIntent())
                .setCallback(ReaderSessionCallback(player, Process.myUid()))
                .setMediaButtonPreferences(mediaButtons())
                .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session?.takeIf { ReaderSessionCallback.admits(controllerInfo) }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val listening =
            player != null &&
                player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        // Keep reading when the app is swiped away mid-listening; otherwise there is nothing to keep alive.
        if (!isPlaybackOngoing || !listening) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** −15 s and +15 s around play/pause in the notification and on the lock screen. */
    private fun mediaButtons(): List<CommandButton> =
        listOf(
            CommandButton
                .Builder(CommandButton.ICON_SKIP_BACK_15)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName(getString(R.string.player_back_15))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton
                .Builder(CommandButton.ICON_SKIP_FORWARD_15)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName(getString(R.string.player_forward_15))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )

    private fun openPlayerIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Stops the service once the last chapter has been read; the position is already saved at the end. */
    private inner class StopAtEndOfBook(
        private val player: Player,
    ) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && !player.currentTimeline.isEmpty) {
                mainHandler.post { pauseAllPlayersAndStopSelf() }
            }
        }
    }
}

/**
 * Lets only trusted controllers in: this app, the system (notification, lock screen, Bluetooth) and apps the
 * listener allowed to control media. Others cannot even read what is playing (SR-7). Requests to play must
 * be book ids; the [ReaderPlayer] resolves them. The custom [ReaderCommands] are granted only to the app's own
 * controllers ([mayCommand]), and their arguments are validated before they reach the player.
 */
@OptIn(UnstableApi::class)
internal class ReaderSessionCallback(
    private val player: ReaderPlayer,
    private val appUid: Int,
) : MediaSession.Callback {
    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.ConnectionResult> =
        Futures.immediateFuture(
            when {
                !admits(controller) -> {
                    MediaSession.ConnectionResult.reject()
                }

                mayCommand(session, controller) -> {
                    val commands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .addSessionCommands(ReaderCommands.all.commands)
                            .build()
                    MediaSession.ConnectionResult
                        .AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                else -> {
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
                }
            },
        )

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> =
        if (mediaItems.all { BookMediaItems.parse(it.mediaId) != null }) {
            Futures.immediateFuture(mediaItems)
        } else {
            Futures.immediateFailedFuture(UnsupportedOperationException("Only book ids can be played"))
        }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val command = ReaderCommands.decode(customCommand.customAction, args)
        val result =
            when {
                !mayCommand(session, controller) -> SessionError.ERROR_PERMISSION_DENIED
                command == null -> SessionError.ERROR_BAD_VALUE
                player.handle(command) -> SessionResult.RESULT_SUCCESS
                else -> SessionError.ERROR_INVALID_STATE
            }
        return Futures.immediateFuture(SessionResult(result))
    }

    /**
     * Whether [controller] may send [ReaderCommands]: a controller running as this app ([appUid], which the system
     * reports and nobody can claim), except Media3's media notification controller. That one relays the custom
     * notification actions that start intents ask for, and any app can send those to the exported service.
     */
    private fun mayCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean = controller.uid == appUid && !session.isMediaNotificationController(controller)

    companion object {
        /**
         * Whether [controller] may reach the session at all: only trusted controllers. Media3 asks about an untrusted
         * caller (unknown package and uid) for media buttons and notification actions that arrive as start intents,
         * which any app can send.
         */
        fun admits(controller: MediaSession.ControllerInfo): Boolean = controller.isTrusted
    }
}
