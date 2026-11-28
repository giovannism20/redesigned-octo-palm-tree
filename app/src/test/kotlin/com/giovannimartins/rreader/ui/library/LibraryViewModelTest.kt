package com.giovannimartins.rreader.ui.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.BrokenLibraryDao
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.importer.FakeImportJobs
import com.giovannimartins.rreader.tts.InMemorySpeakingRateStore
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.ui.player.RecordingControls
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Under Robolectric for Room and the Android log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: RReaderDatabase
    private lateinit var dao: BrokenLibraryDao
    private lateinit var viewModel: LibraryViewModel

    private val controls = RecordingControls()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RReaderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = BrokenLibraryDao(database.bookDao())
        viewModel =
            LibraryViewModel(
                repository = LibraryRepository(dao, BookFileStore(folder.root), FakeImportJobs(), Dispatchers.IO),
                playback = controls,
                settings =
                    SettingsRepository(
                        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "s.preferences_pb") },
                    ),
                rates = SpeakingRateTracker(InMemorySpeakingRateStore(), scope),
            )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        scope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a library that cannot be read is reported, not thrown, and can be read again`() =
        runBlocking<Unit> {
            assertThat(viewModel.state.first { it != LibraryUiState.Loading }).isEqualTo(LibraryUiState.Unavailable)

            dao.broken = false
            database.bookDao().insert(book())
            viewModel.retry()

            val content = viewModel.state.first { it is LibraryUiState.Content } as LibraryUiState.Content
            assertThat(content.books.map { it.title }).containsExactly("Dom Casmurro")
        }

    private fun book() =
        BookEntity(
            id = "00000000-0000-4000-8000-000000000001",
            title = "Dom Casmurro",
            authors = emptyList(),
            language = null,
            description = null,
            format = "EPUB",
            sourceSha256 = "a",
            sourceSize = 1,
            status = BookStatus.READY,
            failureReason = null,
            totalChars = 100,
            chapterCount = 1,
            hasCover = false,
            addedAt = 0,
            lastOpenedAt = null,
            archived = false,
            voiceId = null,
        )

    @Test
    fun `removing a book closes it in the player first`() {
        viewModel.remove("00000000-0000-0000-0000-000000000009")

        assertThat(controls.calls).contains("closeBook 00000000-0000-0000-0000-000000000009")
    }
}
