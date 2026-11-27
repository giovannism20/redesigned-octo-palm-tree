package com.giovannimartins.rreader.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class CuratedCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the curated list shipped with the app is complete, each book once`() {
        val raw = context.assets.open(CuratedCatalog.ASSET).use { it.readBytes().decodeToString() }

        val entries = runBlocking { CuratedCatalog(context, Dispatchers.Unconfined).entries() }

        assertThat(entries).hasSize(Json.parseToJsonElement(raw).jsonArray.size)
        assertThat(entries.size).isAtLeast(MIN_BOOKS)
        assertThat(entries.map { it.id }).containsNoDuplicates()
        entries.forEach { entry ->
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.author).isNotEmpty()
            assertThat(entry.description.orEmpty().length).isIn(1..MAX_DESCRIPTION)
        }
        assertThat(entries.first()).isEqualTo(
            CatalogEntry(
                55752,
                "Dom Casmurro",
                "Machado de Assis",
                "Bento Santiago relembra a juventude e o ciúme que sente de Capitu.",
            ),
        )
    }

    @Test
    fun `leaves out incomplete and repeated entries`() {
        val json =
            """[
              {"id": 55752, "title": " Dom Casmurro ", "author": "Machado de Assis", "description": "Capitu."},
              {"id": 55752, "title": "Repetido", "author": "Machado de Assis", "description": "De novo."},
              {"id": 0, "title": "Sem número", "author": "Alguém", "description": "Nada."},
              {"id": 67740, "title": "  ", "author": "José de Alencar", "description": "Sem título."},
              {"id": 16425, "title": "Amor de Perdição", "author": "", "description": "Sem autor."}
            ]"""

        assertThat(CuratedCatalog.parse(json))
            .containsExactly(CatalogEntry(55752, "Dom Casmurro", "Machado de Assis", "Capitu."))
    }

    @Test
    fun `a list that cannot be read is empty`() {
        val missing = CuratedCatalog({ throw IOException("No such asset") }, Dispatchers.Unconfined)
        val broken = CuratedCatalog({ "não é JSON".byteInputStream() }, Dispatchers.Unconfined)

        assertThat(runBlocking { missing.entries() }).isEmpty()
        assertThat(runBlocking { broken.entries() }).isEmpty()
    }

    private companion object {
        const val MIN_BOOKS = 20
        const val MAX_DESCRIPTION = 100
    }
}
