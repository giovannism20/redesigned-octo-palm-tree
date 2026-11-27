package com.giovannimartins.rreader.tts.neural

import android.content.Context
import com.giovannimartins.rreader.nativecall.NativeCallNote
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The calls the neural voices make into native code (SR-4): starting espeak-ng, loading a voice model, and speaking a
 * segment (espeak-ng's phonemes, then ONNX Runtime). A crash in either kills the process, and the playback service
 * with it; without a note, every resume would render the same segment and die again, and WorkManager would run the
 * pre-render job into it again and again. Renders run one at a time, so one note serves the whole app
 * (`noBackupFilesDir/neural.inflight`, [NativeCallNote]); it is read once per process, before its first call.
 * Keys never hold book text: the package, or the voice and a hash of the segment's text ([NeuralCalls]).
 */
@Singleton
class NeuralCallMarkers internal constructor(
    private val file: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.noBackupFilesDir, FILE_NAME))

    /** Created at the first call of the process, so it reads what the process before it left behind. */
    private val note by lazy { NativeCallNote(file) }

    /** Whether the process died in the call named [key] twice in a row, so it must not be made again. */
    fun shouldSkip(key: String): Boolean = note.shouldSkip(key)

    /** Makes the call named [key] by running [block], noted on disk until it returns or throws. */
    fun <T> run(
        key: String,
        block: () -> T,
    ): T = note.run(key, block)

    /** Forgets that the call named [key] killed the process, so it is made again. */
    fun forget(key: String) = note.forget(key)

    private companion object {
        const val FILE_NAME = "neural.inflight"
    }
}

/** The keys under which [NeuralCallMarkers] notes each native call: ids and a hash, never book text (SR-8). */
internal object NeuralCalls {
    /** Starting espeak-ng, once per process. */
    const val START_PHONEMIZER: String = "phonemizer"

    private const val DIGEST_BYTES = 8

    /** Loading the model of [pkg]. */
    fun load(pkg: VoicePackage): String = "load ${pkg.id}"

    /**
     * Speaking [text] with [voice]: its phonemes and the model's audio. A hash of the text names the segment, since the
     * text is what makes the native code crash, wherever it appears in the book.
     */
    fun speak(
        voice: NeuralVoice,
        text: String,
    ): String = "speak ${voice.key} ${digest(text)}"

    private fun digest(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .take(DIGEST_BYTES)
            .joinToString("") { "%02x".format(it) }
}
