package com.giovannimartins.rreader.ui.player

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.BookEntity
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.importer.FakeImportJobs
import com.giovannimartins.rreader.playback.SleepTimerState
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.tts.neural.VoicePackage
import com.giovannimartins.rreader.tts.neural.VoicePackageStore
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

/** The owner's device test: the voice could not be changed during a reading. The player's "Voice" chooser. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class VoiceChooserTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controls = RecordingControls()
    private val dataScope = CoroutineScope(UnconfinedTestDispatcher())
    private lateinit var database: RReaderDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var store: VoicePackageStore
    private lateinit var choices: VoiceChoices
    private val chosen = mutableListOf<Pair<String, Boolean>>()
    private var openedVoices = false
    private val dora = NeuralVoices.all.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(context, RReaderDatabase::class.java).allowMainThreadQueries().build()
        settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = dataScope) {
                    File(folder.root, "settings.preferences_pb")
                },
            )
        store = VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined)
        val library =
            LibraryRepository(database.bookDao(), BookFileStore(folder.root), FakeImportJobs(), Dispatchers.Unconfined)
        choices = VoiceChoices(settings, library, store)
        runBlocking { database.bookDao().insert(book()) }
    }

    @After
    fun tearDown() {
        dataScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `lists the voices ready on the phone and marks the one reading the book`() {
        install(NeuralVoices.KOKORO)

        val menu = runBlocking { choices.forBook(BOOK).first() }

        assertThat(menu.choices.map { it.voiceId })
            .containsExactly("neural:dora", "neural:alex", "neural:santa", VoiceIds.PHONE)
            .inOrder()
        assertThat(menu.choices.single { it.inUse }.voiceId).isEqualTo(VoiceIds.PHONE)
    }

    @Test
    fun `a voice for this book only, or for every book`() {
        install(NeuralVoices.KOKORO)

        runBlocking { choices.choose(BOOK, dora.id, forAllBooks = false) }
        assertThat(runBlocking { database.bookDao().findById(BOOK)?.voiceId }).isEqualTo(dora.id)
        assertThat(runBlocking { choices.forBook(BOOK).first() }.choices.single { it.inUse }.voiceId).isEqualTo(dora.id)

        runBlocking { choices.choose(BOOK, "neural:alex", forAllBooks = true) }
        assertThat(runBlocking { database.bookDao().findById(BOOK)?.voiceId }).isNull()
        assertThat(runBlocking { settings.settings.first().defaultVoiceId }).isEqualTo("neural:alex")
    }

    @Test
    fun `a book whose own voice was deleted shows the voice that reads it now`() {
        install(NeuralVoices.CADU)
        runBlocking {
            database.bookDao().setVoice(BOOK, dora.id)
            settings.setDefaultVoice("neural:cadu")
        }

        val menu = runBlocking { choices.forBook(BOOK).first() }

        assertThat(menu.choices.single { it.inUse }.voiceId).isEqualTo("neural:cadu")
    }

    @Test
    fun `choosing in the player saves the choice, then tells the player at once`() {
        install(NeuralVoices.KOKORO)
        controls.state.value = previewNowPlaying().copy(bookId = BOOK)
        val viewModel = VoiceChooserViewModel(controls, choices)
        try {
            assertThat(runBlocking { viewModel.menu.first { it.choices.size > 1 } }.choices).hasSize(4)

            viewModel.choose(dora.id, forAllBooks = false)

            assertThat(runBlocking { database.bookDao().findById(BOOK)?.voiceId }).isEqualTo(dora.id)
            assertThat(controls.calls).containsExactly("refreshVoice")
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `the player's Voice button opens the chooser, which asks for which books`() {
        install(NeuralVoices.KOKORO)
        val menu = runBlocking { choices.forBook(BOOK).first() }
        show(menu)

        compose.onNodeWithText("Voice").performClick()
        compose.onNodeWithText("Choose the voice").assertExists()
        compose.onNodeWithText("In use").assertExists()
        compose.onNodeWithText("Dora").assertHeightIsAtLeast(28.dp)
        compose.onNodeWithText("Dora").performClick()
        compose.onNodeWithText("Read with Dora:").assertExists()
        compose.onNodeWithText("This book only").assertHeightIsAtLeast(56.dp).performClick()

        compose.onNodeWithText("Voice").performClick()
        compose.onNodeWithText("Alex").performClick()
        compose.onNodeWithText("All books").performClick()

        compose.onNodeWithText("Voice").performClick()
        compose.onNodeWithText("Download more voices").performClick()

        assertThat(chosen).containsExactly(dora.id to false, "neural:alex" to true).inOrder()
        assertThat(openedVoices).isTrue()
    }

    private fun show(menu: VoiceMenu) {
        compose.setContent {
            RReaderTheme {
                PlayerScreen(
                    nowPlaying = previewNowPlaying(),
                    sleepTimer = SleepTimerState.Off,
                    onMinimize = {},
                    onTogglePlayPause = {},
                    onSeekBack = {},
                    onSeekForward = {},
                    onSeekTo = {},
                    onSelectChapter = {},
                    onSpeedChange = {},
                    onSetSleepTimer = {},
                    onCancelSleepTimer = {},
                    onRetry = {},
                    onOpenVoices = { openedVoices = true },
                    onOpenReader = {},
                    voices = menu,
                    onChooseVoice = { voiceId, forAll -> chosen += voiceId to forAll },
                )
            }
        }
    }

    private fun install(pkg: VoicePackage) {
        pkg.files.forEach { file ->
            RandomAccessFile(
                store.file(pkg, file.name).apply { parentFile?.mkdirs() },
                "rw",
            ).use { it.setLength(file.bytes) }
        }
        runBlocking { store.rescan() }
    }

    private fun book() =
        BookEntity(
            id = BOOK,
            title = "Iracema",
            authors = emptyList(),
            language = null,
            description = null,
            format = "EPUB",
            sourceSha256 = "abc",
            sourceSize = 1,
            status = BookStatus.READY,
            failureReason = null,
            totalChars = 10,
            chapterCount = 1,
            hasCover = false,
            addedAt = 0,
            lastOpenedAt = null,
            archived = false,
            voiceId = null,
        )

    private companion object {
        const val BOOK = "00000000-0000-4000-8000-000000000009"
    }
}
