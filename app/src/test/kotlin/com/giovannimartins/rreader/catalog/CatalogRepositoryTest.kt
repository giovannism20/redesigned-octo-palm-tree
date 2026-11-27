package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ATOM
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ok
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.status
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

/** Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {
    private val transport = FakeTransport()
    private val clock = MutableClock()
    private val repository =
        CatalogRepository(
            CuratedCatalog({ CURATED_JSON.byteInputStream() }, Dispatchers.Unconfined),
            CatalogHttp(transport, Dispatchers.Unconfined),
            clock,
        )

    @Test
    fun `the curated classics need no network`() {
        assertThat(runBlocking { repository.curated() }).containsExactly(DOM_CASMURRO, IRACEMA).inOrder()
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `searches Gutenberg's books in Portuguese with one request for one page`() {
        transport.answer(SEARCH) {
            ok(TestFeeds.page(Triple(55752, "Dom Casmurro", "Machado de Assis"), next = NEXT_RELATIVE), ATOM)
        }

        val result = runBlocking { repository.search("  machado ") }

        assertThat(SEARCH).isEqualTo("https://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt")
        assertThat(result)
            .isEqualTo(
                SearchResult.Found(
                    SearchPage("machado", listOf(CatalogEntry(55752, "Dom Casmurro", "Machado de Assis")), NEXT),
                ),
            )
        assertThat(transport.requests.map { it.address }).containsExactly(SEARCH)
    }

    @Test
    fun `sends the words as typed, encoded, with the Portuguese filter`() {
        assertThat(GutenbergEndpoints.search("são paulo & cia"))
            .isEqualTo("https://www.gutenberg.org/ebooks/search.opds/?query=s%C3%A3o+paulo+%26+cia+l.pt")
    }

    @Test
    fun `asks for the next page only when told to`() {
        transport.answer(SEARCH) {
            ok(TestFeeds.page(Triple(55752, "Dom Casmurro", "Machado"), next = NEXT_RELATIVE), ATOM)
        }
        transport.answer(NEXT) { ok(TestFeeds.page(Triple(54829, "Memorias Posthumas", "Machado")), ATOM) }
        val first = (runBlocking { repository.search("machado") } as SearchResult.Found).page
        assertThat(transport.requests).hasSize(1)

        val second = (runBlocking { repository.next(first) } as SearchResult.Found).page

        assertThat(second)
            .isEqualTo(SearchPage("machado", listOf(CatalogEntry(54829, "Memorias Posthumas", "Machado")), null))
        assertThat(transport.requests.map { it.address }).containsExactly(SEARCH, NEXT).inOrder()
        assertThat(runBlocking { repository.next(second) })
            .isEqualTo(SearchResult.Found(SearchPage("machado", emptyList(), null)))
        assertThat(transport.requests).hasSize(2)
    }

    @Test
    fun `a search repeated within fifteen minutes is answered from memory`() {
        transport.answer(SEARCH) { ok(TestFeeds.page(Triple(55752, "Dom Casmurro", "Machado de Assis")), ATOM) }
        val first = runBlocking { repository.search("machado") }

        clock.advance(Duration.ofMinutes(14))
        val again = runBlocking { repository.search("machado") }
        clock.advance(Duration.ofMinutes(2))
        val later = runBlocking { repository.search("machado") }

        assertThat(again).isEqualTo(first)
        assertThat(later).isEqualTo(first)
        assertThat(transport.requests).hasSize(2)
    }

    @Test
    fun `without a connection the search says the phone is offline`() {
        assertThat(runBlocking { repository.search("machado") })
            .isEqualTo(SearchResult.Failed(SearchFailure.OFFLINE))
    }

    @Test
    fun `a site down for maintenance or too busy is said so`() {
        listOf(503, 500, 429).forEach { code ->
            transport.answer(SEARCH) { status(code) }

            assertThat(runBlocking { repository.search("machado") })
                .isEqualTo(SearchResult.Failed(SearchFailure.SITE_DOWN))
        }
    }

    @Test
    fun `an answer that is not a page of results makes the search unavailable`() {
        listOf(
            { status(403) },
            { ok("<html>Down for maintenance</html>".toByteArray(), "text/html") },
            { ok("Service Unavailable".toByteArray(), ATOM) },
        ).forEach { answer ->
            transport.answer(SEARCH, answer)

            assertThat(runBlocking { repository.search("machado") })
                .isEqualTo(SearchResult.Failed(SearchFailure.UNAVAILABLE))
        }
    }

    @Test
    fun `a next page off the allowlist is never offered`() {
        transport.answer(SEARCH) {
            ok(TestFeeds.page(Triple(55752, "Dom Casmurro", "Machado"), next = "https://mirror.example/page2"), ATOM)
        }

        val page = (runBlocking { repository.search("machado") } as SearchResult.Found).page

        assertThat(page.next).isNull()
    }

    private companion object {
        val SEARCH = GutenbergEndpoints.search("machado")
        const val NEXT_RELATIVE = "/ebooks/search.opds/?query=machado+l.pt&start_index=26"
        const val NEXT = "https://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt&start_index=26"
    }
}
