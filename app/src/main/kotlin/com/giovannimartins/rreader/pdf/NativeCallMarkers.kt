package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.files.BookFileStore
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** A call into native code that an import makes on a book's PDF, where a crash cannot be caught. */
data class NativeCall(
    val stage: NativeStage,
    /** The page, for [NativeStage.OCR_PAGE]; 0 otherwise. */
    val page: Int = 0,
)

/** What the native call does. */
enum class NativeStage {
    /** Drawing the first page as the cover ([PdfCoverRenderer]). */
    COVER,

    /** Opening the file for text recognition ([PageTextRecognizer.open]). */
    OCR_OPEN,

    /** Drawing and recognizing one page ([PageTextRecognizer.Session.recognize]). */
    OCR_PAGE,
}

/**
 * Notes on disk which native call an import is making (the platform PDF renderer, ML Kit), from just before the call
 * until it returns, in [BookFileStore.nativeCallFile]. A crash in native code kills the process with nothing to catch,
 * and WorkManager then runs the import again; the note left behind tells that run which call killed the previous one
 * (SR-4). A call in which the process died [MAX_DEATHS] runs in a row is not made again: a crashing page is left
 * without text and a crashing cover is left out, so a single bad page does not lose the book, while a process killed
 * once for another reason (memory, the user) does not lose a page.
 */
class NativeCallMarkers
    @Inject
    constructor(
        private val files: BookFileStore,
    ) {
        /** The guard of a run of [bookId]'s import that starts now, which knows what the previous run left behind. */
        fun forRun(bookId: String): NativeCallGuard {
            val file = files.nativeCallFile(bookId)
            return NativeCallGuard(file, read(file))
        }

        private fun read(file: File): NativeCallGuard.Interrupted? = noteIn(file)?.let(::parse)

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

        /** The call named by [note], `STAGE PAGE DEATHS`, now with one death more, or null when it does not read so. */
        private fun parse(note: String): NativeCallGuard.Interrupted? {
            val fields = note.trim().split(' ').takeIf { it.size == FIELDS } ?: return null
            val stage = NativeStage.entries.firstOrNull { it.name == fields[0] }
            val page = fields[1].toIntOrNull()?.takeIf { it >= 0 }
            val deaths = fields[2].toIntOrNull()?.takeIf { it >= 0 }
            return if (stage == null || page == null || deaths == null) {
                null
            } else {
                NativeCallGuard.Interrupted(NativeCall(stage, page), deaths + 1)
            }
        }

        companion object {
            /** Runs that died in the same call, in a row, after which the call is skipped. */
            const val MAX_DEATHS: Int = 2

            private const val FIELDS = 3
            private const val MAX_FILE_BYTES = 64L
        }
    }

/** The native calls of one run of an import; see [NativeCallMarkers]. Used from one thread. */
class NativeCallGuard internal constructor(
    private val file: File,
    private val interrupted: Interrupted?,
) {
    /** A call in which the process died, and how many runs in a row died in it. */
    internal data class Interrupted(
        val call: NativeCall,
        val deaths: Int,
    )

    /** Whether the process died in [call] [NativeCallMarkers.MAX_DEATHS] runs in a row, so it must not be made. */
    fun shouldSkip(call: NativeCall): Boolean =
        interrupted?.call == call && interrupted.deaths >= NativeCallMarkers.MAX_DEATHS

    /** Makes [call] by running [block], with a note naming it on disk until [block] returns or throws. */
    fun <T> run(
        call: NativeCall,
        block: () -> T,
    ): T {
        val deaths = interrupted?.takeIf { it.call == call }?.deaths ?: 0
        note("${call.stage.name} ${call.page} $deaths")
        try {
            return block()
        } finally {
            file.delete()
        }
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
}
