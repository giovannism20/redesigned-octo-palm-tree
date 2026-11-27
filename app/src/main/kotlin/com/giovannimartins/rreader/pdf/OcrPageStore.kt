package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.textkit.layout.LayoutLine
import com.giovannimartins.rreader.textkit.layout.PageLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A page read by text recognition, and how long reading it took. */
data class StoredOcrPage(
    val page: PageLayout,
    val elapsed: Duration,
)

/**
 * Keeps the pages that text recognition read for a book while its import runs (ADR 0015), each in
 * [BookFileStore.ocrPageFile] as versioned JSON, so a stopped import resumes at the first page not stored yet.
 *
 * A page file that cannot be read back (damaged, too large, written by another format version or for another page)
 * is ignored: its page is only recognized again. A page that cannot be written is not stored, which costs the same
 * after an interruption. Recognized text is never logged (SR-8).
 */
class OcrPageStore
    @Inject
    constructor(
        private val files: BookFileStore,
    ) {
        /** The page at [pageIndex] of book [bookId], or null when it was not stored or cannot be read back. */
        fun read(
            bookId: String,
            pageIndex: Int,
        ): StoredOcrPage? {
            val file = files.ocrPageFile(bookId, pageIndex)
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
            return try {
                Json
                    .decodeFromString<PageFile>(file.readText())
                    .takeIf { it.version == VERSION && it.index == pageIndex && it.elapsedMillis >= 0 }
                    ?.toStoredPage()
            } catch (e: IOException) {
                unreadable(e)
            } catch (e: IllegalArgumentException) {
                // Also SerializationException, and broken values that a page or a line refuses.
                unreadable(e)
            }
        }

        /** Stores [page] of book [bookId], which recognition read in [elapsed]. */
        fun write(
            bookId: String,
            page: PageLayout,
            elapsed: Duration,
        ) {
            try {
                files.writeOcrPage(bookId, page.index, Json.encodeToString(page.toFile(elapsed)).toByteArray())
            } catch (e: IOException) {
                Diagnostics.recordHandled("store recognized page", e)
            } catch (e: SerializationException) {
                Diagnostics.recordHandled("store recognized page", e)
            }
        }

        private fun unreadable(error: Exception): StoredOcrPage? {
            Diagnostics.recordHandled("read stored recognized page", error)
            return null
        }

        @Serializable
        private data class PageFile(
            val version: Int,
            val index: Int,
            val width: Float,
            val height: Float,
            val elapsedMillis: Long,
            val lines: List<LineFile>,
        ) {
            fun toStoredPage() =
                StoredOcrPage(
                    PageLayout(index, width, height, lines.map(LineFile::toLayoutLine)),
                    elapsedMillis.milliseconds,
                )
        }

        @Serializable
        private data class LineFile(
            val text: String,
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
            val fontSize: Float,
        ) {
            fun toLayoutLine() = LayoutLine(text, left, top, right, bottom, fontSize)
        }

        private fun PageLayout.toFile(elapsed: Duration) =
            PageFile(
                version = VERSION,
                index = index,
                width = width,
                height = height,
                elapsedMillis = elapsed.inWholeMilliseconds,
                lines = lines.map { LineFile(it.text, it.left, it.top, it.right, it.bottom, it.fontSize) },
            )

        private companion object {
            /** Bumped whenever stored pages would no longer mean the same, so older ones are recognized again. */
            const val VERSION = 1

            /** Far above any real page; a larger file is not trusted. */
            const val MAX_FILE_BYTES = 4L * 1024 * 1024
        }
    }
