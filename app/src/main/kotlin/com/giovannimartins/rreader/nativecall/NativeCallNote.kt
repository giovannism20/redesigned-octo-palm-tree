package com.giovannimartins.rreader.nativecall

import com.giovannimartins.rreader.Diagnostics
import java.io.File
import java.io.IOException

/**
 * Notes in [file] which call into native code is being made, from just before the call until it returns (SR-4). A
 * crash in native code kills the process with nothing to catch; the note it leaves behind tells the next process which
 * call killed the previous one. A call in which [MAX_DEATHS] processes in a row died is not made again ([shouldSkip]),
 * while a process killed once for another reason (memory, the user) loses nothing.
 *
 * Calls are named by short keys (at most about 50 characters) made of ids, numbers and hashes, never book text
 * (SR-8). The note left behind is read when this object is created, so create it before the first call of a run (or
 * of a process) and use it from one thread at a time.
 */
class NativeCallNote(
    private val file: File,
) {
    /** The call a process died in, as the note left behind names it, with that death counted; null without one. */
    private var interrupted: Interrupted? = noteIn(file)?.let(::parse)

    /** Whether the process died in the call named [key] [MAX_DEATHS] times in a row, so it must not be made again. */
    fun shouldSkip(key: String): Boolean = interrupted?.let { it.key == key && it.deaths >= MAX_DEATHS } == true

    /** Makes the call named [key] by running [block], with a note naming it on disk until [block] returns or throws. */
    fun <T> run(
        key: String,
        block: () -> T,
    ): T {
        val deaths = interrupted?.takeIf { it.key == key }?.deaths ?: 0
        note("$key $deaths")
        try {
            return block()
        } finally {
            file.delete()
        }
    }

    /** Forgets that processes died in the call named [key], so it is made again, for example once its input changed. */
    fun forget(key: String) {
        if (interrupted?.key == key) interrupted = null
        if (noteIn(file)?.let(::parse)?.key == key) file.delete()
    }

    /** Writes the note whole or not at all; without it the call is still made, only unprotected. */
    private fun note(text: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writeText(text)
            if (!temp.renameTo(file)) throw IOException("Could not move the native call marker into place")
        } catch (e: IOException) {
            temp.delete()
            Diagnostics.recordHandled("write native call marker", e)
        }
    }

    /** A call in which the process died, and how many processes in a row died in it. */
    private data class Interrupted(
        val key: String,
        val deaths: Int,
    )

    companion object {
        /** Processes that died in the same call, in a row, after which the call is skipped. */
        const val MAX_DEATHS: Int = 2

        private const val MAX_FILE_BYTES = 64L

        /** The text of the note in [file], or null when there is none, or it is too large or unreadable. */
        private fun noteIn(file: File): String? {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
            return try {
                file.readText()
            } catch (e: IOException) {
                Diagnostics.recordHandled("read native call marker", e)
                null
            }
        }

        /** The call named by [note], `KEY DEATHS`, now with one death more, or null when it does not read so. */
        private fun parse(note: String): Interrupted? {
            val text = note.trim()
            val separator = text.lastIndexOf(' ')
            val key = text.take(separator.coerceAtLeast(0)).trim()
            val deaths = text.substring(separator + 1).toIntOrNull()?.takeIf { it >= 0 }
            return if (separator <= 0 || key.isEmpty() || deaths == null) null else Interrupted(key, deaths + 1)
        }
    }
}
