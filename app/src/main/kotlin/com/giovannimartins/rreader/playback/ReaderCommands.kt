package com.giovannimartins.rreader.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** A command the app sends to the playback service beyond the standard player commands. */
sealed interface ReaderCommand {
    /** Plays from the start of the segment that holds [position] (the reader's "read from here"). */
    data class ReadFrom(
        val position: ReadingPosition,
    ) : ReaderCommand

    /** Starts the sleep timer, replacing a running one. */
    data class SetSleepTimer(
        val setting: SleepTimerSetting,
    ) : ReaderCommand

    data object CancelSleepTimer : ReaderCommand
}

/**
 * The custom session commands that carry [ReaderCommand]s from the app's `MediaController` to the playback
 * service. Only the app's own controller is granted them, and their arguments are validated on arrival.
 */
object ReaderCommands {
    private const val READ_FROM = "com.giovannimartins.rreader.command.READ_FROM"
    private const val SET_SLEEP_TIMER = "com.giovannimartins.rreader.command.SET_SLEEP_TIMER"
    private const val CANCEL_SLEEP_TIMER = "com.giovannimartins.rreader.command.CANCEL_SLEEP_TIMER"
    private const val KEY_CHAPTER = "chapter"
    private const val KEY_OFFSET = "offset"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_END_OF_CHAPTER = "end_of_chapter"

    /** The longest sleep timer accepted. */
    private val MAX_SLEEP = 12.hours

    /** Every custom command, as granted to the app's controller. */
    val all: SessionCommands
        get() =
            SessionCommands
                .Builder()
                .add(command(READ_FROM))
                .add(command(SET_SLEEP_TIMER))
                .add(command(CANCEL_SLEEP_TIMER))
                .build()

    /** The session command and its arguments for [command]. */
    fun encode(command: ReaderCommand): Pair<SessionCommand, Bundle> =
        when (command) {
            is ReaderCommand.ReadFrom -> {
                val args =
                    Bundle().apply {
                        putInt(KEY_CHAPTER, command.position.chapterIndex)
                        putInt(KEY_OFFSET, command.position.charOffset)
                    }
                command(READ_FROM) to args
            }

            is ReaderCommand.SetSleepTimer -> {
                val args = Bundle()
                when (val setting = command.setting) {
                    is SleepTimerSetting.After -> args.putLong(KEY_DURATION_MS, setting.duration.inWholeMilliseconds)
                    SleepTimerSetting.EndOfChapter -> args.putBoolean(KEY_END_OF_CHAPTER, true)
                }
                command(SET_SLEEP_TIMER) to args
            }

            ReaderCommand.CancelSleepTimer -> {
                command(CANCEL_SLEEP_TIMER) to Bundle()
            }
        }

    /** The command sent as [action] with [args], or null when it is unknown or its arguments are invalid. */
    fun decode(
        action: String,
        args: Bundle,
    ): ReaderCommand? =
        when (action) {
            READ_FROM -> readingPosition(args)?.let(ReaderCommand::ReadFrom)
            SET_SLEEP_TIMER -> sleepTimerSetting(args)?.let(ReaderCommand::SetSleepTimer)
            CANCEL_SLEEP_TIMER -> ReaderCommand.CancelSleepTimer
            else -> null
        }

    private fun readingPosition(args: Bundle): ReadingPosition? {
        val chapter = args.getInt(KEY_CHAPTER, -1)
        val offset = args.getInt(KEY_OFFSET, -1)
        return if (chapter >= 0 && offset >= 0) ReadingPosition(chapter, offset) else null
    }

    /** The end of the chapter, or a duration of up to [MAX_SLEEP]; null for anything else. */
    private fun sleepTimerSetting(args: Bundle): SleepTimerSetting? {
        val duration = args.getLong(KEY_DURATION_MS, -1).milliseconds
        return when {
            args.getBoolean(KEY_END_OF_CHAPTER, false) -> SleepTimerSetting.EndOfChapter
            duration.isPositive() && duration <= MAX_SLEEP -> SleepTimerSetting.After(duration)
            else -> null
        }
    }

    private fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
}
