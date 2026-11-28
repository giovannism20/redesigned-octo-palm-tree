package com.giovannimartins.rreader.playback

import android.os.Bundle
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class ReaderCommandsTest {
    @Test
    fun `read from travels as a granted session command`() {
        val readFrom = ReaderCommand.ReadFrom(ReadingPosition(3, 120))

        val (command, args) = ReaderCommands.encode(readFrom)

        assertThat(ReaderCommands.all.contains(command)).isTrue()
        assertThat(ReaderCommands.decode(command.customAction, args)).isEqualTo(readFrom)
    }

    @Test
    fun `sleep timer and voice commands travel as granted session commands`() {
        val commands =
            listOf(
                ReaderCommand.SetSleepTimer(SleepTimerSetting.After(30.minutes)),
                ReaderCommand.SetSleepTimer(SleepTimerSetting.EndOfChapter),
                ReaderCommand.CancelSleepTimer,
                ReaderCommand.RefreshVoice,
                ReaderCommand.CloseBook("00000000-0000-0000-0000-000000000001"),
            )

        commands.forEach { readerCommand ->
            val (command, args) = ReaderCommands.encode(readerCommand)
            assertThat(ReaderCommands.all.contains(command)).isTrue()
            assertThat(ReaderCommands.decode(command.customAction, args)).isEqualTo(readerCommand)
        }
    }

    @Test
    fun `sleep timers last more than nothing and at most twelve hours`() {
        listOf(0.milliseconds, (-5).milliseconds, 13.hours).forEach { duration ->
            val (command, args) = ReaderCommands.encode(ReaderCommand.SetSleepTimer(SleepTimerSetting.After(duration)))
            assertThat(ReaderCommands.decode(command.customAction, args)).isNull()
            assertThat(ReaderCommands.decode(command.customAction, Bundle())).isNull()
        }
    }

    @Test
    fun `unknown commands and invalid arguments are rejected`() {
        val (command, args) = ReaderCommands.encode(ReaderCommand.ReadFrom(ReadingPosition(1, 2)))
        val negative = Bundle(args).apply { keySet().forEach { putInt(it, -1) } }

        assertThat(ReaderCommands.decode(command.customAction, Bundle())).isNull()
        assertThat(ReaderCommands.decode(command.customAction, negative)).isNull()
        assertThat(ReaderCommands.decode("com.example.UNKNOWN", args)).isNull()
        val (close, _) = ReaderCommands.encode(ReaderCommand.CloseBook(BOOK_ID))
        assertThat(ReaderCommands.decode(close.customAction, Bundle().apply { putString("book_id", "../x") })).isNull()
    }
}
