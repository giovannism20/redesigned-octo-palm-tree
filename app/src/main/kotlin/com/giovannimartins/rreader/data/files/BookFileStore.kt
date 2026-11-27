package com.giovannimartins.rreader.data.files

import android.content.Context
import com.giovannimartins.rreader.textkit.BookFormat
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.CoverImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-disk layout of imported books inside private app storage:
 *
 * ```
 * files/books/{bookId}/source.{epub,pdf}
 * files/books/{bookId}/chapters/{index}.txt
 * files/books/{bookId}/cover
 * files/books/{bookId}/ocr/{page}.json   (pages read by text recognition while the import runs)
 * files/books/{bookId}/native.inflight   (the native call the import is making, while it makes it)
 * files/imports/            (temporary files while copying)
 * ```
 *
 * Book ids are generated UUIDs; anything else is rejected so no caller can
 * build a path outside this layout.
 */
@Singleton
@Suppress("TooManyFunctions") // It owns every path of a book's storage; splitting it would scatter the layout.
class BookFileStore internal constructor(
    filesDir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.filesDir)

    private val booksRoot = File(filesDir, "books")
    private val importsRoot = File(filesDir, "imports")

    /** A fresh temporary file for an incoming import. */
    fun newImportTempFile(): File {
        importsRoot.mkdirs()
        return File(importsRoot, "${UUID.randomUUID()}.part")
    }

    fun sourceFile(
        bookId: String,
        format: BookFormat,
    ): File = File(bookDir(bookId), "source.${format.name.lowercase()}")

    fun coverFile(bookId: String): File = File(bookDir(bookId), "cover")

    fun chapterFile(
        bookId: String,
        index: Int,
    ): File {
        require(index >= 0) { "Chapter index must not be negative" }
        return File(File(bookDir(bookId), "chapters"), "$index.txt")
    }

    /** Where the page at [pageIndex] read by text recognition is kept while the book's import runs. */
    fun ocrPageFile(
        bookId: String,
        pageIndex: Int,
    ): File {
        require(pageIndex >= 0) { "Page index must not be negative" }
        return File(File(bookDir(bookId), OCR_DIRECTORY), "$pageIndex.json")
    }

    /** Where an import notes the call into native code it is making, while it makes it (`NativeCallMarkers`). */
    fun nativeCallFile(bookId: String): File = File(bookDir(bookId), NATIVE_CALL_FILE)

    /** Moves a completed temporary import into place as the book's source file. */
    @Throws(IOException::class)
    fun adoptSource(
        temp: File,
        bookId: String,
        format: BookFormat,
    ): File {
        val target = sourceFile(bookId, format)
        target.parentFile?.mkdirs()
        if (!temp.renameTo(target)) throw IOException("Could not move the imported file into place")
        return target
    }

    /** Writes every chapter text, replacing previous ones. */
    @Throws(IOException::class)
    fun writeChapters(
        bookId: String,
        chapters: List<Chapter>,
    ) {
        File(bookDir(bookId), "chapters").deleteRecursively()
        chapters.forEachIndexed {
            index,
            chapter,
            ->
            writeAtomically(chapterFile(bookId, index), chapter.text.toByteArray())
        }
    }

    @Throws(IOException::class)
    fun readChapterText(
        bookId: String,
        index: Int,
    ): String = chapterFile(bookId, index).readText()

    @Throws(IOException::class)
    fun writeCover(
        bookId: String,
        cover: CoverImage,
    ) = writeAtomically(coverFile(bookId), cover.bytes)

    /** Writes the page at [pageIndex] read by text recognition, replacing it whole or not at all. */
    @Throws(IOException::class)
    fun writeOcrPage(
        bookId: String,
        pageIndex: Int,
        bytes: ByteArray,
    ) = writeAtomically(ocrPageFile(bookId, pageIndex), bytes)

    /** Removes the pages text recognition stored for a book, once its import is over. */
    fun deleteOcrPages(bookId: String) {
        File(bookDir(bookId), OCR_DIRECTORY).deleteRecursively()
    }

    /** Removes what an import keeps to resume after it was stopped: recognized pages and the native call marker. */
    fun deleteResumeFiles(bookId: String) {
        deleteOcrPages(bookId)
        nativeCallFile(bookId).delete()
    }

    /** Removes everything stored for a book. */
    fun delete(bookId: String) {
        bookDir(bookId).deleteRecursively()
    }

    private fun bookDir(bookId: String): File {
        require(UUID_PATTERN.matches(bookId)) { "Invalid book id" }
        return File(booksRoot, bookId)
    }

    private fun writeAtomically(
        target: File,
        bytes: ByteArray,
    ) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not write ${target.name}")
        }
    }

    private companion object {
        const val OCR_DIRECTORY = "ocr"
        const val NATIVE_CALL_FILE = "native.inflight"
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
