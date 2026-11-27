package com.giovannimartins.rreader.importer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * A content provider of another app that shares [content] and misbehaves on demand: it throws, as providers may, the
 * exceptions a client does not expect when its file is opened or its name is asked for.
 */
internal class HostileProvider : ContentProvider() {
    enum class Failure { NONE, OPEN, NAME }

    var failure = Failure.NONE
    var content = ByteArray(0)

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (failure == Failure.NAME) throw IllegalStateException("the provider cannot tell the name")
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("livro.epub")) }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (failure == Failure.OPEN) throw IllegalArgumentException("the provider refuses the file")
        val file = File.createTempFile("shared", ".epub").apply { writeBytes(content) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/epub+zip"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.example.hostile"
        val BOOK: Uri = Uri.parse("content://$AUTHORITY/livro.epub")
    }
}
