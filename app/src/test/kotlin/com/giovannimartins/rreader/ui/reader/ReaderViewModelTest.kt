package com.giovannimartins.rreader.ui.reader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.playback.BOOK_ID
import com.giovannimartins.rreader.playback.FakePlaybackContent
import com.giovannimartins.rreader.playback.ListeningStatus
import com.giovannimartins.rreader.playback.PlaybackContent
import com.giovannimartins.rreader.playback.SpokenSegment
import com.giovannimartins.rreader.playback.book
import com.giovannimartins.rreader.playback.chapterText
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.ui.player.RecordingControls
import com.giovannimartins.rreader.ui.player.previewNowPlaying
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import java.io.File
import java.io.IOException

/** Under Robolectric because failures are logged with the Android log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val controls = RecordingControls()
    private val status = ListeningStatus()
    private val planner = SegmentPlanner()
    private val twoParagraphs =
        "Primeiro parágrafo do capítulo dois, com uma frase.\n\n" +
            "Segundo parágrafo do capítulo dois, com outra frase."
    private val texts = listOf(chapterText(0, 6), twoParagraphs, chapterText(2, 4))
    private val content = FakePlaybackContent(book(texts), texts)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the chapter being read with the segment being spoken highlighted`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 1, segment = 1)

            val state = viewModel.awaitState { it.paragraphs != null && it.highlight != null }

            assertThat(state.chapterIndex).isEqualTo(1)
            assertThat(state.chapterTitle).isEqualTo("Capítulo II — Do livro")
            assertThat(state.paragraphs)
                .containsExactlyElementsIn(
                    Chapter.paragraphRangesOf(twoParagraphs).map {
                        twoParagraphs.substring(it.start, it.end)
                    },
                ).inOrder()
            assertThat(
                state.highlight,
            ).isEqualTo(ReaderHighlight(1, 0, "Segundo parágrafo do capítulo dois, com outra frase.".length))
            assertThat(state.following).isTrue()
        }

    @Test
    fun `follows the reading into the next chapter`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 1, segment = 1)
            viewModel.awaitState { it.chapterIndex == 1 && it.paragraphs != null }

            listenTo(chapter = 2, segment = 0)

            val state = viewModel.awaitState { it.chapterIndex == 2 && it.highlight != null }
            assertThat(state.highlight?.paragraph).isEqualTo(0)
        }

    @Test
    fun `scrolling away keeps the chapter shown until the listener goes back to the reading`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 1, segment = 0)
            viewModel.awaitState { it.highlight != null }

            viewModel.onUserScroll()
            listenTo(chapter = 2, segment = 0)

            val away = viewModel.awaitState { !it.following }
            assertThat(away.chapterIndex).isEqualTo(1)
            assertThat(away.highlight).isNull()

            viewModel.backToReading()

            val back = viewModel.awaitState { it.chapterIndex == 2 && it.highlight != null }
            assertThat(back.following).isTrue()
            assertThat(back.followRequests).isEqualTo(away.followRequests + 1)
        }

    @Test
    fun `tapping a sentence reads from the start of its segment`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 1, segment = 0)
            viewModel.awaitState { it.paragraphs != null }
            val second = planner.plan(twoParagraphs)[1]

            viewModel.readFrom(paragraph = 1, offset = 10)

            assertThat(controls.calls).containsExactly("readFrom 1:${second.start}")
        }

    @Test
    fun `reading from a chapter left behind keeps it shown until the voice gets there`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 1, segment = 0)
            viewModel.awaitState { it.paragraphs != null }
            viewModel.onUserScroll()
            listenTo(chapter = 2, segment = 0)

            viewModel.readFrom(paragraph = 1, offset = 0)
            listenTo(chapter = 2, segment = 1)

            assertThat(viewModel.awaitState { it.following }.chapterIndex).isEqualTo(1)
            listenTo(chapter = 1, segment = 1)
            val arrived = viewModel.awaitState { it.highlight != null }
            assertThat(arrived.chapterIndex).isEqualTo(1)
            assertThat(arrived.highlight?.paragraph).isEqualTo(1)
        }

    @Test
    fun `the text size steps through its sizes and is saved`() =
        runTest {
            val viewModel = viewModel()
            listenTo(chapter = 0, segment = 0)

            viewModel.enlargeText()
            assertThat(viewModel.awaitState { it.textScale != 1f }.textScale).isEqualTo(1.15f)

            viewModel.shrinkText()
            viewModel.awaitState { it.textScale == 1f }
            viewModel.shrinkText()
            val smallest = viewModel.awaitState { it.textScale == 0.85f }
            assertThat(smallest.canShrinkText).isFalse()
            assertThat(smallest.canEnlargeText).isTrue()
        }

    @Test
    fun `a chapter that cannot be read says so`() =
        runTest {
            val broken =
                object : PlaybackContent by content {
                    override suspend fun chapterText(
                        bookId: String,
                        chapterIndex: Int,
                    ): String = throw IOException("unreadable")
                }
            val viewModel = viewModel(broken)
            listenTo(chapter = 0, segment = 0)

            val state = viewModel.awaitState { it.failed }

            assertThat(state.paragraphs).isNull()
        }

    private fun TestScope.viewModel(source: PlaybackContent = content): ReaderViewModel {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }
        val viewModel =
            ReaderViewModel(
                controls = controls,
                status = status,
                content = source,
                planner = planner,
                settings = SettingsRepository(dataStore),
                cpu = UnconfinedTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    /** The player is in [chapter] and the voice reads its segment number [segment]. */
    private fun listenTo(
        chapter: Int,
        segment: Int,
    ) {
        controls.state.value = previewNowPlaying().copy(bookId = BOOK_ID, chapterIndex = chapter)
        status.publishSegment(SpokenSegment(BOOK_ID, chapter, planner.plan(texts[chapter])[segment]))
    }

    private suspend fun ReaderViewModel.awaitState(predicate: (ReaderUiState) -> Boolean): ReaderUiState =
        state.first(predicate)
}
