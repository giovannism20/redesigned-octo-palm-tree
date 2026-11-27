package com.giovannimartins.rreader.playback

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderSessionCallbackTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val texts = listOf(chapterText(0, 6))
    private lateinit var fixture: ReaderPlayerFixture
    private lateinit var callback: ReaderSessionCallback
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        fixture = ReaderPlayerFixture(folder.root, FakePlaybackContent(book(texts), texts))
        callback = ReaderSessionCallback(fixture.player, Process.myUid())
        session = MediaSession.Builder(context, fixture.player).setCallback(callback).build()
    }

    @After
    fun tearDown() {
        session.release()
        fixture.player.release()
    }

    @Test
    fun `only the app's own controllers get the reader commands and untrusted ones are rejected`() {
        val app = callback.onConnectAsync(session, controller(Process.myUid())).get()
        val system = callback.onConnectAsync(session, controller(SYSTEM_UID)).get()
        val untrusted = callback.onConnectAsync(session, controller(OTHER_UID, trusted = false)).get()

        assertThat(app.isAccepted).isTrue()
        assertThat(app.availableSessionCommands.commands).containsAtLeastElementsIn(ReaderCommands.all.commands)
        assertThat(system.isAccepted).isTrue()
        assertThat(system.availableSessionCommands.commands).containsNoneIn(ReaderCommands.all.commands)
        assertThat(untrusted.isAccepted).isFalse()
    }

    @Test
    fun `SR-7 the media notification controller gets the player commands but not the reader commands`() {
        val ui = callback.onConnectAsync(session, appController()).get()
        val notification = callback.onConnectAsync(session, appController(notification = true)).get()

        assertThat(session.isMediaNotificationController(appController(notification = true))).isTrue()
        assertThat(ui.availableSessionCommands.commands).containsAtLeastElementsIn(ReaderCommands.all.commands)
        assertThat(notification.isAccepted).isTrue()
        assertThat(notification.availableSessionCommands.commands).containsNoneIn(ReaderCommands.all.commands)
        // The notification's -15 s and +15 s buttons are player commands.
        assertThat(notification.availablePlayerCommands.contains(Player.COMMAND_SEEK_BACK)).isTrue()
        assertThat(notification.availablePlayerCommands.contains(Player.COMMAND_SEEK_FORWARD)).isTrue()
    }

    @Test
    fun `SR-7 reader commands relayed by the media notification are refused before reaching the player`() {
        fixture.open()
        advance(fixture.player).untilState(Player.STATE_READY)
        val (command, args) = ReaderCommands.encode(ReaderCommand.ReadFrom(ReadingPosition(0, 50)))

        val relayed = callback.onCustomCommand(session, appController(notification = true), command, args).get()

        assertThat(relayed.resultCode).isEqualTo(SessionError.ERROR_PERMISSION_DENIED)
        assertThat(fixture.player.playWhenReady).isFalse()
        assertThat(callback.onCustomCommand(session, appController(), command, args).get().resultCode)
            .isEqualTo(SessionResult.RESULT_SUCCESS)
    }

    @Test
    fun `SR-7 the service gives its session to trusted controllers only, not to start intents from other apps`() {
        // What Media3 asks about for a media button or notification action sent to the service as a start intent.
        val startIntentCaller =
            MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                "androidx.media3.session.MediaSessionService",
                UNKNOWN,
                UNKNOWN,
                MediaLibraryInfo.VERSION_INT,
                MediaLibraryInfo.INTERFACE_VERSION,
                false,
                Bundle.EMPTY,
                true,
            )

        assertThat(ReaderSessionCallback.admits(appController())).isTrue()
        assertThat(ReaderSessionCallback.admits(controller(SYSTEM_UID))).isTrue()
        assertThat(ReaderSessionCallback.admits(startIntentCaller)).isFalse()
        assertThat(ReaderSessionCallback.admits(controller(OTHER_UID, trusted = false))).isFalse()
    }

    @Test
    fun `read from plays the open book there and invalid requests are refused`() {
        val app = controller(Process.myUid())
        val (command, args) = ReaderCommands.encode(ReaderCommand.ReadFrom(ReadingPosition(0, 50)))
        assertThat(callback.onCustomCommand(session, app, command, args).get().resultCode)
            .isEqualTo(SessionError.ERROR_INVALID_STATE)

        fixture.open()
        advance(fixture.player).untilState(Player.STATE_READY)

        assertThat(callback.onCustomCommand(session, app, command, args).get().resultCode)
            .isEqualTo(SessionResult.RESULT_SUCCESS)
        assertThat(fixture.player.playWhenReady).isTrue()
        assertThat(callback.onCustomCommand(session, app, command, Bundle()).get().resultCode)
            .isEqualTo(SessionError.ERROR_BAD_VALUE)
    }

    @Test
    fun `a voice change reaches the player from the app only, and needs an open book`() {
        val (command, args) = ReaderCommands.encode(ReaderCommand.RefreshVoice)
        assertThat(callback.onCustomCommand(session, appController(), command, args).get().resultCode)
            .isEqualTo(SessionError.ERROR_INVALID_STATE)

        fixture.open()
        advance(fixture.player).untilState(Player.STATE_READY)

        assertThat(callback.onCustomCommand(session, appController(), command, args).get().resultCode)
            .isEqualTo(SessionResult.RESULT_SUCCESS)
        assertThat(
            callback.onCustomCommand(session, appController(notification = true), command, args).get().resultCode,
        ).isEqualTo(SessionError.ERROR_PERMISSION_DENIED)
    }

    /** A Media3 controller of this app: the UI's, or with [notification] the media notification's. */
    private fun appController(notification: Boolean = false) =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            context.packageName,
            Process.myPid(),
            Process.myUid(),
            MediaLibraryInfo.VERSION_INT,
            MediaLibraryInfo.INTERFACE_VERSION,
            true,
            Bundle().apply { putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, notification) },
            true,
        )

    private fun controller(
        uid: Int,
        trusted: Boolean = true,
    ) = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.example.controller",
        0,
        uid,
        0,
        0,
        trusted,
        Bundle.EMPTY,
        true,
    )

    private companion object {
        /** The pid and uid Media3 gives the caller of a start intent: unknown. */
        const val UNKNOWN = -1
        const val SYSTEM_UID = 1000
        const val OTHER_UID = 10_999
    }
}
