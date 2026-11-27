package com.giovannimartins.rreader.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.giovannimartins.rreader.textkit.model.ReadingPosition

/** A command the app sends to the playback service beyond the standard player commands. */
sealed interface ReaderCommand {
    /** Plays from the start of the segment that holds [position] (the reader's "read from here"). */
    data class ReadFrom(
        val position: ReadingPosition,
    ) : ReaderCommand
}

/**
 * The custom session commands that carry [ReaderCommand]s from the app's `MediaController` to the playback
 * service. Only the app's own controller is granted them, and their arguments are validated on arrival.
 */
object ReaderCommands {
    private const val READ_FROM = "com.giovannimartins.rreader.command.READ_FROM"
    private const val KEY_CHAPTER = "chapter"
    private const val KEY_OFFSET = "offset"

    /** Every custom command, as granted to the app's controller. */
    val all: SessionCommands get() = SessionCommands.Builder().add(command(READ_FROM)).build()

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
        }

    /** The command sent as [action] with [args], or null when it is unknown or its arguments are invalid. */
    fun decode(
        action: String,
        args: Bundle,
    ): ReaderCommand? =
        when (action) {
            READ_FROM -> readingPosition(args)?.let(ReaderCommand::ReadFrom)
            else -> null
        }

    private fun readingPosition(args: Bundle): ReadingPosition? {
        val chapter = args.getInt(KEY_CHAPTER, -1)
        val offset = args.getInt(KEY_OFFSET, -1)
        return if (chapter >= 0 && offset >= 0) ReadingPosition(chapter, offset) else null
    }

    private fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
}
