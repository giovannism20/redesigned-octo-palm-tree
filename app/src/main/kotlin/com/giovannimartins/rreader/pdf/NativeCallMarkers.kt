package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.nativecall.NativeCallNote
import javax.inject.Inject

/** A call into native code that an import makes on a book's PDF, where a crash cannot be caught. */
data class NativeCall(
    val stage: NativeStage,
    /** The page, for [NativeStage.OCR_PAGE]; 0 otherwise. */
    val page: Int = 0,
) {
    /** How the note on disk names the call: `STAGE PAGE`. */
    internal val key: String get() = "${stage.name} $page"
}

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
 * until it returns, in [BookFileStore.nativeCallFile] ([NativeCallNote]). A crash in native code kills the process with
 * nothing to catch, and WorkManager then runs the import again; the note left behind tells that run which call killed
 * the previous one (SR-4). A call in which the process died [MAX_DEATHS] runs in a row is not made again: a crashing
 * page is left without text and a crashing cover is left out, so a single bad page does not lose the book, while a
 * process killed once for another reason (memory, the user) does not lose a page.
 */
class NativeCallMarkers
    @Inject
    constructor(
        private val files: BookFileStore,
    ) {
        /** The guard of a run of [bookId]'s import that starts now, which knows what the previous run left behind. */
        fun forRun(bookId: String): NativeCallGuard = NativeCallGuard(NativeCallNote(files.nativeCallFile(bookId)))

        companion object {
            /** Runs that died in the same call, in a row, after which the call is skipped. */
            const val MAX_DEATHS: Int = NativeCallNote.MAX_DEATHS
        }
    }

/** The native calls of one run of an import; see [NativeCallMarkers]. Used from one thread. */
class NativeCallGuard internal constructor(
    private val note: NativeCallNote,
) {
    /** Whether the process died in [call] [NativeCallMarkers.MAX_DEATHS] runs in a row, so it must not be made. */
    fun shouldSkip(call: NativeCall): Boolean = note.shouldSkip(call.key)

    /** Makes [call] by running [block], with a note naming it on disk until [block] returns or throws. */
    fun <T> run(
        call: NativeCall,
        block: () -> T,
    ): T = note.run(call.key, block)
}
