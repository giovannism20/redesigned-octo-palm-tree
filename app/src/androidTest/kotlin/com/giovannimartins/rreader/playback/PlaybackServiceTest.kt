package com.giovannimartins.rreader.playback

import android.content.ComponentName
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
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
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

    private companion object {
        const val CONNECT_TIMEOUT_S = 10L
        const val FIFTEEN_SECONDS_MS = 15_000L
    }
}
