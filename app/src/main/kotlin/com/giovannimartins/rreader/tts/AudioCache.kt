package com.giovannimartins.rreader.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Where rendered segment audio lives: `cache/tts/{bookId}/{voiceHash}/{chapter}-{start}-{end}.wav`, where
 * `voiceHash` hashes the voice's id and render version; audio rendered at another speed than 1.0× ([RenderSpeeds])
 * ends in `@x{speed × 100}.wav`.
 * Everything here can be regenerated, so the system may clear it at any time.
 */
@Singleton
class AudioCache internal constructor(
    cacheDir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.cacheDir)

    private val root = File(cacheDir, "tts")

    /**
     * File for one rendered segment of a chapter with a given voice, filed under [voiceKey] (a voice's `audioKey`: its
     * id and render version), rendered at [speed].
     */
    fun segmentFile(
        bookId: String,
        voiceKey: String,
        chapterIndex: Int,
        start: Int,
        end: Int,
        speed: Float = RenderSpeeds.NORMAL,
    ): File {
        require(BOOK_ID_PATTERN.matches(bookId)) { "Invalid book id" }
        val suffix = if (speed == RenderSpeeds.NORMAL) "" else "@x${(speed * PERCENT).roundToInt()}"
        return File(File(File(root, bookId), hash(voiceKey)), "$chapterIndex-$start-$end$suffix.wav")
    }

    /** Scratch file for one-off renders such as voice samples. */
    fun sampleFile(voiceId: String): File = File(File(root, "samples"), "${hash(voiceId)}.wav")

    /** Deletes the least recently used files until the cache fits in [maxBytes], never touching [keep]. */
    fun trim(
        maxBytes: Long,
        keep: Set<File> = emptySet(),
    ) {
        val files = root.walkTopDown().filter(File::isFile).toList()
        var total = files.sumOf(File::length)
        val oldestFirst = files.filterNot { it in keep }.sortedBy(File::lastModified).iterator()
        while (total > maxBytes && oldestFirst.hasNext()) {
            val file = oldestFirst.next()
            total -= file.length()
            file.delete()
        }
    }

    /** Removes all audio rendered for a book (for example when it is deleted). */
    fun deleteBook(bookId: String) {
        require(BOOK_ID_PATTERN.matches(bookId)) { "Invalid book id" }
        File(root, bookId).deleteRecursively()
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance(
                "SHA-256",
            ).digest(value.toByteArray())
            .take(HASH_BYTES)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val HASH_BYTES = 8
        const val PERCENT = 100
        val BOOK_ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
