package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.textkit.gutenberg.GutenbergFeedParser
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A page of catalogue search results. */
data class SearchPage(
    /** The words searched for, as the listener typed them (trimmed). */
    val terms: String,
    val entries: List<CatalogEntry>,
    /** The address of the next page, already checked against [CatalogHosts]; null on the last page. */
    val next: String?,
)

/** Why a search failed, as the listener is told. */
enum class SearchFailure {
    /** No connection to the internet, or it broke. */
    OFFLINE,

    /** Gutenberg did not answer with a page of results. */
    UNAVAILABLE,
}

/** Outcome of a catalogue search. */
sealed interface SearchResult {
    data class Found(
        val page: SearchPage,
    ) : SearchResult

    data class Failed(
        val failure: SearchFailure,
    ) : SearchResult
}

/**
 * The free catalogue: the curated classics, which need no network, and searches in Project Gutenberg's books in
 * Portuguese. A search makes one request for one page of 25 results, and the next page is asked for only when the
 * listener wants more, as Gutenberg asks of catalogue apps (ADR 0017). Pages are kept in memory for a while
 * ([SearchCache]), so going back to a search does not ask again; nothing is written to disk.
 */
@Singleton
class CatalogRepository
    @Inject
    constructor(
        private val curated: CuratedCatalog,
        private val http: CatalogHttp,
        clock: Clock,
    ) {
        private val cache = SearchCache(clock)

        /** The curated classics, in the order they are shown. */
        suspend fun curated(): List<CatalogEntry> = curated.entries()

        /** The first page of books in Portuguese matching [terms], which must not be blank. */
        suspend fun search(terms: String): SearchResult {
            val words = terms.trim()
            require(words.isNotEmpty()) { "Nothing to search for" }
            return load(words, GutenbergEndpoints.search(words))
        }

        /** The page after [page]; an empty last page when there is none. */
        suspend fun next(page: SearchPage): SearchResult =
            page.next?.let { load(page.terms, it) } ?: SearchResult.Found(SearchPage(page.terms, emptyList(), null))

        private suspend fun load(
            terms: String,
            address: String,
        ): SearchResult {
            cache[address]?.let { return SearchResult.Found(it) }
            val body = ByteArrayOutputStream()
            return when (val result = http.fetch(CatalogRequest(address, FEED_TYPES, MAX_FEED_BYTES), body)) {
                is FetchResult.Failed -> SearchResult.Failed(failureOf(result.failure))
                is FetchResult.Received -> read(terms, address, body.toByteArray())
            }
        }

        private fun read(
            terms: String,
            address: String,
            body: ByteArray,
        ): SearchResult {
            // Gutenberg serves its feeds in UTF-8; anything malformed becomes replacement characters.
            val feed = GutenbergFeedParser.parse(body.decodeToString())
            return if (feed == null) {
                SearchResult.Failed(SearchFailure.UNAVAILABLE)
            } else {
                val page =
                    SearchPage(
                        terms = terms,
                        entries = feed.books.map { CatalogEntry(it.id, it.title, it.author) },
                        next = feed.next?.let { nextAddress(address, it) },
                    )
                cache[address] = page
                SearchResult.Found(page)
            }
        }

        /** The next page's address, [link] as the feed gives it, when the app may contact it. */
        private fun nextAddress(
            address: String,
            link: String,
        ): String? = CatalogHosts.check(address)?.let { CatalogHosts.resolve(it, link) }?.toString()

        private fun failureOf(failure: FetchFailure): SearchFailure =
            if (failure == FetchFailure.NETWORK) SearchFailure.OFFLINE else SearchFailure.UNAVAILABLE

        private companion object {
            val FEED_TYPES = setOf("application/atom+xml")

            /** A page of 25 results is about 60 KB. */
            const val MAX_FEED_BYTES = 1024L * 1024
        }
    }

/**
 * The last [CAPACITY] search pages fetched, each kept for [LIFETIME], in memory only: searches are private, and the
 * catalogue changes slowly.
 */
internal class SearchCache(
    private val clock: Clock,
) {
    private val pages =
        object : LinkedHashMap<String, Cached>(CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > CAPACITY
        }

    @Synchronized
    operator fun get(address: String): SearchPage? {
        val cached = pages[address] ?: return null
        val fresh = Duration.between(cached.storedAt, clock.instant()) < LIFETIME
        if (!fresh) pages.remove(address)
        return cached.page.takeIf { fresh }
    }

    @Synchronized
    operator fun set(
        address: String,
        page: SearchPage,
    ) {
        pages[address] = Cached(page, clock.instant())
    }

    private class Cached(
        val page: SearchPage,
        val storedAt: Instant,
    )

    companion object {
        const val CAPACITY = 20
        val LIFETIME: Duration = Duration.ofMinutes(15)
        private const val LOAD_FACTOR = 0.75f
    }
}
