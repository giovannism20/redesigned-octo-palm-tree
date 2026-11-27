package com.giovannimartins.rreader.playback

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Connects to the real playback service the way the UI does. Run on a phone or emulator with
 * `./gradlew :app:connectedDebugAndroidTest`.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theAppConnectsAndGetsFifteenSecondControls() {
        val controller = connect()
        try {
            instrumentation.runOnMainSync {
                assertThat(controller.isCommandAvailable(Player.COMMAND_SEEK_BACK)).isTrue()
                assertThat(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)).isTrue()
                assertThat(controller.seekBackIncrement).isEqualTo(FIFTEEN_SECONDS_MS)
                assertThat(controller.seekForwardIncrement).isEqualTo(FIFTEEN_SECONDS_MS)
                assertThat(controller.mediaButtonPreferences.map { it.icon })
                    .containsExactly(CommandButton.ICON_SKIP_BACK_15, CommandButton.ICON_SKIP_FORWARD_15)
                    .inOrder()
            }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    /**
     * SR-7: the service is exported, so any app can send it the start intents Media3 handles: media buttons and
     * custom notification actions, addressed to the session URI that Media3's default session id gives, or to none.
     * The session must neither act on them nor lose its own controller's reader commands.
     */
    @Test
    fun mediaButtonsAndNotificationActionsSentAsStartIntentsDoNotReachTheSession() {
        val controller = connect()
        try {
            val before = snapshot(controller)

            hostileStartIntents().forEach { intent -> context.startService(intent) }
            SystemClock.sleep(SETTLE_MS)

            instrumentation.runOnMainSync {
                assertThat(controller.isConnected).isTrue()
                assertThat(snapshot(controller)).isEqualTo(before)
                assertThat(controller.availableSessionCommands.commands)
                    .containsAtLeastElementsIn(ReaderCommands.all.commands)
            }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    private fun connect(): MediaController {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return MediaController.Builder(context, token).buildAsync().get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
    }

    /** What a hostile media button or notification action would change. */
    private fun snapshot(controller: MediaController): List<Any> {
        var state = emptyList<Any>()
        instrumentation.runOnMainSync {
            state =
                listOf(
                    controller.playWhenReady,
                    controller.playbackState,
                    controller.mediaItemCount,
                    controller.currentPosition,
                )
        }
        return state
    }

    private fun hostileStartIntents(): List<Intent> {
        val service = ComponentName(context, PlaybackService::class.java)

        fun mediaButton(
            keyCode: Int,
            sessionUri: Uri?,
        ) = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(service)
            .setData(sessionUri)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

        val (sleepTimer, args) = ReaderCommands.encode(ReaderCommand.SetSleepTimer(SleepTimerSetting.After(1.minutes)))
        val notificationAction =
            Intent(CUSTOM_NOTIFICATION_ACTION)
                .setComponent(service)
                .setData(DEFAULT_SESSION_URI)
                .putExtra(EXTRAS_KEY_CUSTOM_ACTION, sleepTimer.customAction)
                .putExtra(EXTRAS_KEY_CUSTOM_ACTION_EXTRAS, args)
        return listOf(
            mediaButton(KeyEvent.KEYCODE_MEDIA_PLAY, DEFAULT_SESSION_URI),
            mediaButton(KeyEvent.KEYCODE_MEDIA_PLAY, null),
            mediaButton(KeyEvent.KEYCODE_MEDIA_NEXT, DEFAULT_SESSION_URI),
            notificationAction,
            Intent(notificationAction).setData(null),
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_S = 10L
        const val FIFTEEN_SECONDS_MS = 15_000L
        const val SETTLE_MS = 1_000L

        /** The URI of a session that keeps Media3's default (empty) id. */
        val DEFAULT_SESSION_URI: Uri = Uri.parse("androidx://media3.session/")

        // Media3's custom notification action and its extras (CustomCommandPendingIntentBuilder).
        const val CUSTOM_NOTIFICATION_ACTION = "androidx.media3.session.CUSTOM_NOTIFICATION_ACTION"
        const val EXTRAS_KEY_CUSTOM_ACTION = "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION"
        const val EXTRAS_KEY_CUSTOM_ACTION_EXTRAS =
            "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION_EXTRAS"
    }
}
