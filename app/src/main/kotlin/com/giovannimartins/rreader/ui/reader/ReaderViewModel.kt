package com.giovannimartins.rreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.di.DefaultDispatcher
import com.giovannimartins.rreader.playback.ListeningStatus
import com.giovannimartins.rreader.playback.PlaybackContent
import com.giovannimartins.rreader.playback.SpokenSegment
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.ui.player.NowPlaying
import com.giovannimartins.rreader.ui.player.PlaybackControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** What the follow-along reader shows. */
data class ReaderUiState(
    /** The chapter shown: the one being read, while the reader follows the reading. */
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    /** The chapter's paragraphs, or null while they load. */
    val paragraphs: List<String>? = null,
    /** Whether the chapter's text could not be read. */
    val failed: Boolean = false,
    /** The segment being read, when it is in the chapter shown. */
    val highlight: ReaderHighlight? = null,
    /** Whether the text keeps the segment being read in view; scrolling away stops it until asked again. */
    val following: Boolean = true,
    /** Grows each time the listener goes back to the reading, so the text scrolls there again. */
    val followRequests: Int = 0,
    /** Text size multiplier on top of the system font scale. */
    val textScale: Float = AppSettings.DEFAULT_TEXT_SCALE,
    /** The typeface the listener chose for the text. */
    val readingFont: ReadingFont = ReadingFont.ATKINSON,
) {
    val canShrinkText: Boolean get() = ReaderTextScale.smaller(textScale) != null
    val canEnlargeText: Boolean get() = ReaderTextScale.larger(textScale) != null
}

/**
 * State and actions of the follow-along reader. It shows the chapter being read, from the playback service's
 * [ListeningStatus], and highlights the segment being spoken. Scrolling away stops following until the
 * listener goes back to the reading; tapping a sentence reads from there through [PlaybackControls.readFrom].
 */
@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val controls: PlaybackControls,
        private val status: ListeningStatus,
        private val content: PlaybackContent,
        private val planner: SegmentPlanner,
        private val settings: SettingsRepository,
        @DefaultDispatcher private val cpu: CoroutineDispatcher,
    ) : ViewModel() {
        private val following = MutableStateFlow(true)
        private val followRequests = MutableStateFlow(0)
        private val shown = MutableStateFlow<ChapterKey?>(null)
        private val loaded = MutableStateFlow<Loaded?>(null)

        /** A chapter the listener chose to read from; the text stays there until the voice reaches it. */
        private var awaited: ChapterKey? = null

        /** The listener's choices for the text: its size and its typeface. */
        private val textLook =
            settings.settings
                .map { TextLook(it.readerTextScale, it.appearance.readingFont) }
                .catch { e ->
                    if (e !is IOException) throw e
                    Diagnostics.recordHandled("read the reader text settings", e)
                    emit(TextLook())
                }

        val state: StateFlow<ReaderUiState> =
            combine(
                shown,
                loaded,
                status.spokenSegment,
                combine(following, followRequests, ::Pair),
                combine(textLook, controls.nowPlaying, ::Pair),
            ) { key, result, spoken, (follow, requests), (look, now) ->
                val current = result?.takeIf { it.key == key }
                ReaderUiState(
                    chapterIndex = key?.chapterIndex ?: 0,
                    chapterTitle = key?.let { now?.chapterTitles?.getOrNull(it.chapterIndex) }.orEmpty(),
                    paragraphs = current?.chapter?.paragraphs,
                    failed = current != null && current.chapter == null,
                    highlight = current?.chapter?.highlightOf(spoken),
                    following = follow,
                    followRequests = requests,
                    textScale = look.scale,
                    readingFont = look.font,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderUiState())

        init {
            viewModelScope.launch {
                combine(controls.nowPlaying, status.spokenSegment, following, ::Triple)
                    .collect { (now, spoken, follow) -> showReading(now, spoken, follow) }
            }
            viewModelScope.launch {
                shown.filterNotNull().collectLatest { key ->
                    loaded.value = null
                    loaded.value = load(key)
                }
            }
        }

        /** The listener scrolled the text: stop keeping the segment being read in view. */
        fun onUserScroll() {
            awaited = null
            following.value = false
        }

        /** Shows the chapter being read again and keeps the segment being read in view. */
        fun backToReading() {
            awaited = null
            following.value = true
            followRequests.update { it + 1 }
        }

        /** Reads from the sentence at character [offset] of paragraph [paragraph] of the chapter shown. */
        fun readFrom(
            paragraph: Int,
            offset: Int,
        ) {
            val chapter = loaded.value?.chapter?.takeIf { it.key == shown.value } ?: return
            val position = chapter.readingPositionAt(paragraph, offset) ?: return
            awaited = chapter.key.takeIf { it != readingChapter(controls.nowPlaying.value, status.spokenSegment.value) }
            following.value = true
            controls.readFrom(position)
        }

        fun enlargeText() = changeTextScale(ReaderTextScale::larger)

        fun shrinkText() = changeTextScale(ReaderTextScale::smaller)

        private fun changeTextScale(step: (Float) -> Float?) {
            viewModelScope.launch {
                try {
                    step(settings.settings.first().readerTextScale)?.let { settings.setReaderTextScale(it) }
                } catch (e: IOException) {
                    Diagnostics.recordHandled("save the reader text size", e)
                }
            }
        }

        /** Moves the text to the chapter being read while following it, or when another book is open. */
        private fun showReading(
            now: NowPlaying?,
            spoken: SpokenSegment?,
            follow: Boolean,
        ) {
            val reading = readingChapter(now, spoken) ?: return
            if (reading == awaited) awaited = null
            val otherBook = shown.value?.bookId != reading.bookId
            if (otherBook) awaited = null
            if (otherBook || (follow && awaited == null)) shown.value = reading
        }

        private suspend fun load(key: ChapterKey): Loaded =
            try {
                val text = content.chapterText(key.bookId, key.chapterIndex)
                Loaded(key, withContext(cpu) { ReaderChapter(key, text, planner) })
            } catch (e: IOException) {
                Diagnostics.recordHandled("read a chapter for the reader", e)
                Loaded(key, null)
            }

        /** The text's size multiplier and typeface. */
        private data class TextLook(
            val scale: Float = AppSettings.DEFAULT_TEXT_SCALE,
            val font: ReadingFont = ReadingFont.ATKINSON,
        )

        /** A loaded chapter, or a chapter that could not be read ([chapter] is null). */
        private class Loaded(
            val key: ChapterKey,
            val chapter: ReaderChapter?,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /** The chapter being read: the spoken segment's, else the player's current chapter. */
            fun readingChapter(
                now: NowPlaying?,
                spoken: SpokenSegment?,
            ): ChapterKey? =
                now?.let { playing ->
                    val chapter = spoken?.takeIf { it.bookId == playing.bookId }?.chapterIndex ?: playing.chapterIndex
                    ChapterKey(playing.bookId, chapter)
                }
        }
    }
