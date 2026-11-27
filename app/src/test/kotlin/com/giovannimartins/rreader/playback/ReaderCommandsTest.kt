package com.giovannimartins.rreader.playback

import android.os.Bundle
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    fun `unknown commands and invalid arguments are rejected`() {
        val (command, args) = ReaderCommands.encode(ReaderCommand.ReadFrom(ReadingPosition(1, 2)))
        val negative = Bundle(args).apply { keySet().forEach { putInt(it, -1) } }

        assertThat(ReaderCommands.decode(command.customAction, Bundle())).isNull()
        assertThat(ReaderCommands.decode(command.customAction, negative)).isNull()
        assertThat(ReaderCommands.decode("com.example.UNKNOWN", args)).isNull()
    }
}
