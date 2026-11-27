package com.giovannimartins.rreader.catalog

import android.content.Context
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The curated classics in Portuguese shown first in Explore, read from [ASSET] inside the app: showing them needs no
 * network. Each entry is a Project Gutenberg ebook number checked against Gutenberg's catalogue, with the title and
 * author as readers know them and a short description written for the app.
 */
@Singleton
class CuratedCatalog internal constructor(
    private val open: () -> InputStream,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this({ context.assets.open(ASSET) }, io)

    @Volatile
    private var entries: List<CatalogEntry>? = null

    /** The curated books in the order they are shown; empty if the list cannot be read. */
    suspend fun entries(): List<CatalogEntry> = entries ?: withContext(io) { read().also { entries = it } }

    private fun read(): List<CatalogEntry> =
        try {
            parse(open().use { it.readBytes().decodeToString() })
        } catch (e: IOException) {
            unreadable(e)
        } catch (e: IllegalArgumentException) {
            // Also SerializationException: a list that does not parse.
            unreadable(e)
        }

    private fun unreadable(error: Exception): List<CatalogEntry> {
        Diagnostics.recordHandled("read the curated catalogue", error)
        return emptyList()
    }

    @Serializable
    private data class CuratedBook(
        val id: Int,
        val title: String,
        val author: String,
        val description: String,
    ) {
        val complete: Boolean get() = id > 0 && title.isNotBlank() && author.isNotBlank() && description.isNotBlank()

        fun toEntry() = CatalogEntry(id, title.trim(), author.trim(), description.trim())
    }

    companion object {
        /** The list inside the app's assets. */
        const val ASSET: String = "catalog/curated.json"

        /** The complete entries of [json], each ebook once, in their order. */
        internal fun parse(json: String): List<CatalogEntry> =
            Json
                .decodeFromString<List<CuratedBook>>(json)
                .filter(CuratedBook::complete)
                .distinctBy(CuratedBook::id)
                .map(CuratedBook::toEntry)
    }
}
