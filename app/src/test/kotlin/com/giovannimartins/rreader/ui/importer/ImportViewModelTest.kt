package com.giovannimartins.rreader.ui.importer

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.HostileProvider
import com.giovannimartins.rreader.importer.RecordingImportJobs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Under Robolectric for Room, content providers and the Android log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RReaderDatabase
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        val intake =
            BookIntake(
                context.contentResolver,
                database.bookDao(),
                BookFileStore(folder.root),
                RecordingImportJobs(),
                Clock.systemUTC(),
                Dispatchers.IO,
            )
        viewModel = ImportViewModel(intake)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a file whose provider throws is reported, not a crash`() =
        runBlocking<Unit> {
            Robolectric.setupContentProvider(HostileProvider::class.java, HostileProvider.AUTHORITY).failure =
                HostileProvider.Failure.OPEN

            viewModel.import(HostileProvider.BOOK)

            // A crash would leave the state as it was, so the wait is bounded.
            val shown = withTimeout(WAIT) { viewModel.state.first { it.message != null } }

            assertThat(shown)
                .isEqualTo(
                    ImportUiState(receiving = false, message = ImportMessage.Rejected(ImportFailure.STORAGE_ERROR)),
                )
        }

    private companion object {
        val WAIT = 10.seconds
    }
}
