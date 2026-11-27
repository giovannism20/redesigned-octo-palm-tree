package com.giovannimartins.rreader.data

import android.database.sqlite.SQLiteBlobTooBigException
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.LibraryRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [BookDao] whose library cannot be read while [broken], as when a stored row is larger than a cursor window; the
 * rest is [dao].
 */
internal class BrokenLibraryDao(
    private val dao: BookDao,
) : BookDao by dao {
    var broken = true

    override fun observeLibrary(): Flow<List<LibraryRow>> =
        flow {
            if (broken) throw SQLiteBlobTooBigException("Row too big to fit into CursorWindow")
            emitAll(dao.observeLibrary())
        }
}
