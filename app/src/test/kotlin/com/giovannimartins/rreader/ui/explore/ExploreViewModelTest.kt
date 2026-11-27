package com.giovannimartins.rreader.ui.explore

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.catalog.CURATED_JSON
import com.giovannimartins.rreader.catalog.CatalogDownloads
import com.giovannimartins.rreader.catalog.CatalogEntry
import com.giovannimartins.rreader.catalog.CatalogHttp
import com.giovannimartins.rreader.catalog.CatalogRepository
import com.giovannimartins.rreader.catalog.CuratedCatalog
import com.giovannimartins.rreader.catalog.DOM_CASMURRO
import com.giovannimartins.rreader.catalog.DownloadState
import com.giovannimartins.rreader.catalog.FakeTransport
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ATOM
import com.giovannimartins.rreader.catalog.FakeTransport.Companion.ok
import com.giovannimartins.rreader.catalog.GutenbergEndpoints
import com.giovannimartins.rreader.catalog.IRACEMA
import com.giovannimartins.rreader.catalog.MutableClock
import com.giovannimartins.rreader.catalog.NetworkStatus
import com.giovannimartins.rreader.catalog.SearchFailure
import com.giovannimartins.rreader.catalog.TestFeeds
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.importer.FakeImportJobs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Under Robolectric for Room and the Android log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExploreViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val transport = FakeTransport()
    private val downloads = FakeCatalogDownloads()
    private val network = FakeNetworkStatus()
    private lateinit var database: RReaderDatabase
    private val viewModels = mutableListOf<ExploreViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RReaderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        // Stop the view models' collectors (WhileSubscribed keeps them alive for a while) before
        // closing the database and restoring Dispatchers.Main, so nothing resumes on Main meanwhile.
        viewModels.forEach { it.viewModelScope.cancel() }
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the curated classics without any request`() =
        runTest {
            val state = viewModel().state.first { it.curated.isNotEmpty() }

            assertThat(state.curated).containsExactly(DOM_CASMURRO, IRACEMA).inOrder()
            assertThat(state.search).isEqualTo(SearchUiState.Idle)
            assertThat(state.online).isTrue()
            assertThat(transport.requests).isEmpty()
        }

    @Test
    fun `says when the phone goes offline and when it is back`() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.first { it.online }

            network.online.value = false
            viewModel.state.first { !it.online }
            network.online.value = true

            assertThat(viewModel.state.first { it.online }.online).isTrue()
        }

    @Test
    fun `searches the words typed and shows the books found`() =
        runTest {
            transport.answer(SEARCH) { ok(page(Triple(55752, "Dom Casmurro", "Machado de Assis"), next = NEXT), ATOM) }
            val viewModel = viewModel()

            viewModel.onQueryChange("machado ")
            viewModel.search()

            val state = viewModel.state.first { it.search is SearchUiState.Results }
            assertThat(state.search)
                .isEqualTo(
                    SearchUiState.Results(
                        "machado",
                        listOf(CatalogEntry(55752, "Dom Casmurro", "Machado de Assis")),
                        canLoadMore = true,
                    ),
                )
            assertThat(transport.requests).hasSize(1)
        }

    @Test
    fun `asks for more results only when the listener wants them`() =
        runTest {
            transport.answer(SEARCH) { ok(page(Triple(55752, "Dom Casmurro", "Machado"), next = NEXT), ATOM) }
            transport.answer(NEXT_ABSOLUTE) { ok(page(Triple(54829, "Memorias Posthumas", "Machado")), ATOM) }
            val viewModel = viewModel()
            viewModel.onQueryChange("machado")
            viewModel.search()
            viewModel.state.first { it.search is SearchUiState.Results }
            assertThat(transport.requests).hasSize(1)

            viewModel.moreResults()

            val results = viewModel.state.first { (it.search as? SearchUiState.Results)?.entries?.size == 2 }.search
            assertThat((results as SearchUiState.Results).entries.map { it.id }).containsExactly(55752, 54829).inOrder()
            assertThat(results.canLoadMore).isFalse()
            viewModel.moreResults()
            assertThat(transport.requests).hasSize(2)
        }

    @Test
    fun `a search without a connection says so and can be tried again`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onQueryChange("alencar")
            viewModel.search()
            val failed = viewModel.state.first { it.search is SearchUiState.Failed }.search

            assertThat(failed).isEqualTo(SearchUiState.Failed("alencar", SearchFailure.OFFLINE))

            transport.answer(GutenbergEndpoints.search("alencar")) {
                ok(page(Triple(67740, "Iracema", "José de Alencar")), ATOM)
            }
            viewModel.onQueryChange("")
            viewModel.retrySearch()

            val state = viewModel.state.first { it.search is SearchUiState.Results }
            assertThat(state.query).isEqualTo("alencar")
            assertThat((state.search as SearchUiState.Results).entries.map { it.id }).containsExactly(67740)
        }

    @Test
    fun `an empty search box shows the suggestions again, without a request`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChange("   ")
            viewModel.search()

            assertThat(viewModel.state.first { it.curated.isNotEmpty() }.search).isEqualTo(SearchUiState.Idle)
            assertThat(transport.requests).isEmpty()
        }

    @Test
    fun `clearing a search returns to the suggestions`() =
        runTest {
            transport.answer(SEARCH) { ok(page(Triple(55752, "Dom Casmurro", "Machado")), ATOM) }
            val viewModel = viewModel()
            viewModel.onQueryChange("machado")
            viewModel.search()
            viewModel.state.first { it.search is SearchUiState.Results }

            viewModel.clearSearch()

            val state = viewModel.state.first { it.search == SearchUiState.Idle }
            assertThat(state.query).isEmpty()
        }

    @Test
    fun `downloads a book in the background and shows how it goes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.download(DOM_CASMURRO)
            downloads.states.value = mapOf(DOM_CASMURRO.id to DownloadState.Downloading(0.5f))

            assertThat(downloads.requested).containsExactly(DOM_CASMURRO)
            assertThat(viewModel.state.first { it.downloads.isNotEmpty() }.downloads)
                .containsExactly(DOM_CASMURRO.id, DownloadState.Downloading(0.5f))
        }

    @Test
    fun `a downloaded book that was removed from the library can be downloaded again`() =
        runTest {
            val viewModel = viewModel()
            val added = DownloadState.InLibrary(BOOK_ID, alreadyThere = false)
            downloads.states.value = mapOf(DOM_CASMURRO.id to added, IRACEMA.id to DownloadState.Waiting)

            val removed = viewModel.state.first { IRACEMA.id in it.downloads }
            assertThat(removed.downloads).doesNotContainKey(DOM_CASMURRO.id)

            database.bookDao().insert(book(BOOK_ID))

            assertThat(viewModel.state.first { DOM_CASMURRO.id in it.downloads }.downloads[DOM_CASMURRO.id])
                .isEqualTo(added)
        }

    private fun viewModel() = newViewModel().also { viewModels += it }

    private fun newViewModel() =
        ExploreViewModel(
            catalog =
                CatalogRepository(
                    CuratedCatalog({ CURATED_JSON.byteInputStream() }, Dispatchers.Unconfined),
                    CatalogHttp(transport, Dispatchers.Unconfined),
                    MutableClock(),
                ),
            downloads = downloads,
            networkStatus = network,
            library =
                LibraryRepository(
                    database.bookDao(),
                    BookFileStore(folder.root),
                    FakeImportJobs(),
                    Dispatchers.Unconfined,
                ),
        )

    private fun page(
        book: Triple<Int, String, String>,
        next: String? = null,
    ) = TestFeeds.page(book, next = next)

    private fun book(id: String) =
        BookEntity(
            id = id,
            title = "Dom Casmurro",
            authors = emptyList(),
            language = null,
            description = null,
            format = "EPUB",
            sourceSha256 = "a",
            sourceSize = 1,
            status = BookStatus.IMPORTING,
            failureReason = null,
            totalChars = 0,
            chapterCount = 0,
            hasCover = false,
            addedAt = 0,
            lastOpenedAt = null,
            archived = false,
            voiceId = null,
        )

    private class FakeCatalogDownloads : CatalogDownloads {
        val requested = mutableListOf<CatalogEntry>()
        override val states = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())

        override fun download(entry: CatalogEntry) {
            requested += entry
        }
    }

    private class FakeNetworkStatus : NetworkStatus {
        override val online = MutableStateFlow(true)
    }

    private companion object {
        val SEARCH = GutenbergEndpoints.search("machado")
        const val NEXT = "/ebooks/search.opds/?query=machado+l.pt&start_index=26"
        const val NEXT_ABSOLUTE = "https://www.gutenberg.org$NEXT"
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
