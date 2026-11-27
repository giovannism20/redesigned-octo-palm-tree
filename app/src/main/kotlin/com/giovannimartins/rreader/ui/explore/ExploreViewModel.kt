package com.giovannimartins.rreader.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.catalog.CatalogDownloads
import com.giovannimartins.rreader.catalog.CatalogEntry
import com.giovannimartins.rreader.catalog.CatalogRepository
import com.giovannimartins.rreader.catalog.DownloadState
import com.giovannimartins.rreader.catalog.NetworkStatus
import com.giovannimartins.rreader.catalog.SearchFailure
import com.giovannimartins.rreader.catalog.SearchPage
import com.giovannimartins.rreader.catalog.SearchResult
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Explore screen shows. */
data class ExploreUiState(
    /** False when the phone has no internet connection, which the screen says. */
    val online: Boolean = true,
    /** The curated classics, shown when there is no search. */
    val curated: List<CatalogEntry> = emptyList(),
    /** The text in the search box. */
    val query: String = "",
    val search: SearchUiState = SearchUiState.Idle,
    /** Where each book's download stands, by ebook number; books never asked for are absent. */
    val downloads: Map<Int, DownloadState> = emptyMap(),
)

/** The search part of the Explore screen. */
sealed interface SearchUiState {
    /** No search: the curated classics are shown. */
    data object Idle : SearchUiState

    data class Loading(
        val terms: String,
    ) : SearchUiState

    /** The books found for [terms] so far; more can be asked for while [canLoadMore]. */
    data class Results(
        val terms: String,
        val entries: List<CatalogEntry>,
        val canLoadMore: Boolean,
        val loadingMore: Boolean = false,
        /** Why the last request for more results failed, until the next one. */
        val moreFailure: SearchFailure? = null,
    ) : SearchUiState

    data class Failed(
        val terms: String,
        val failure: SearchFailure,
    ) : SearchUiState
}

/**
 * The free catalogue: the curated classics, searches in Project Gutenberg's books in Portuguese and downloads into
 * the library. Nothing reaches the network until the listener searches or asks for a book; a search asks for one page
 * of results, and the next one only when the listener wants more. Downloads run in the background
 * ([CatalogDownloads]); a book that was removed from the library since can be downloaded again.
 */
@HiltViewModel
class ExploreViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val downloads: CatalogDownloads,
        networkStatus: NetworkStatus,
        library: LibraryRepository,
    ) : ViewModel() {
        private val curated = MutableStateFlow<List<CatalogEntry>>(emptyList())
        private val query = MutableStateFlow("")
        private val search = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        private var lastPage: SearchPage? = null
        private var searchJob: Job? = null

        /** The ids of the books in the library, or null once it cannot be read, which must not close the app. */
        private val libraryIds: Flow<Set<String>?> =
            library.books
                .map<List<LibraryBook>, Set<String>?> { books -> books.mapTo(HashSet()) { it.id } }
                .catch { e ->
                    Diagnostics.recordHandled("read the library for Explore", e)
                    emit(null)
                }

        /** The ebook numbers of the books the screen shows: the curated classics and the books found. */
        private val shownIds: Flow<Set<Int>> =
            combine(curated, search) { classics, search ->
                val found = (search as? SearchUiState.Results)?.entries.orEmpty()
                (classics + found).mapTo(HashSet()) { it.id }
            }.distinctUntilChanged()

        /**
         * The downloads of the books shown, leaving out books that are no longer in the library (all of them kept when
         * that is unknown).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val downloadStates: Flow<Map<Int, DownloadState>> =
            combine(shownIds.flatMapLatest(downloads::states), libraryIds) { states, ids ->
                states.filterValues { it !is DownloadState.InLibrary || ids == null || it.bookId in ids }
            }

        val state: StateFlow<ExploreUiState> =
            combine(
                networkStatus.online,
                curated,
                query,
                search,
                downloadStates.onStart { emit(emptyMap()) },
            ) { online, curated, query, search, downloads ->
                ExploreUiState(online, curated, query, search, downloads)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExploreUiState())

        init {
            viewModelScope.launch { curated.value = catalog.curated() }
        }

        fun onQueryChange(text: String) {
            query.value = text.take(MAX_QUERY_LENGTH)
        }

        /** Searches for the words in the search box; an empty box shows the curated classics again. */
        fun search() {
            val terms = query.value.trim()
            if (terms.isEmpty()) clearSearch() else search(terms)
        }

        /** Searches again for the words of the search that failed. */
        fun retrySearch() {
            val failed = search.value as? SearchUiState.Failed
            if (failed != null) {
                query.value = failed.terms
                search(failed.terms)
            }
        }

        /** Asks for the next page of results, while there is one and no request is under way. */
        fun moreResults() {
            val current = (search.value as? SearchUiState.Results)?.takeIf { it.canLoadMore && !it.loadingMore }
            val page = lastPage
            if (current != null && page != null) loadMore(current, page)
        }

        fun clearSearch() {
            searchJob?.cancel()
            lastPage = null
            query.value = ""
            search.value = SearchUiState.Idle
        }

        /** Downloads [entry] into the library in the background. */
        fun download(entry: CatalogEntry) = downloads.download(entry)

        /** Stops the download of [entry], waiting or under way; it can be downloaded again. */
        fun cancelDownload(entry: CatalogEntry) = downloads.cancel(entry.id)

        private fun search(terms: String) {
            searchJob?.cancel()
            lastPage = null
            search.value = SearchUiState.Loading(terms)
            searchJob =
                viewModelScope.launch {
                    search.value =
                        when (val result = catalog.search(terms)) {
                            is SearchResult.Found -> {
                                lastPage = result.page
                                SearchUiState.Results(
                                    terms,
                                    result.page.entries,
                                    canLoadMore = result.page.next != null,
                                )
                            }

                            is SearchResult.Failed -> {
                                SearchUiState.Failed(terms, result.failure)
                            }
                        }
                }
        }

        private fun loadMore(
            current: SearchUiState.Results,
            page: SearchPage,
        ) {
            search.value = current.copy(loadingMore = true, moreFailure = null)
            searchJob =
                viewModelScope.launch {
                    search.value =
                        when (val result = catalog.next(page)) {
                            is SearchResult.Found -> {
                                lastPage = result.page
                                current.copy(
                                    entries = (current.entries + result.page.entries).distinctBy { it.id },
                                    canLoadMore = result.page.next != null,
                                    loadingMore = false,
                                )
                            }

                            is SearchResult.Failed -> {
                                current.copy(loadingMore = false, moreFailure = result.failure)
                            }
                        }
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_QUERY_LENGTH = 100
        }
    }
