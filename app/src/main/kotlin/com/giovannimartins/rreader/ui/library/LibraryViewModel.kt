package com.giovannimartins.rreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.ui.player.PlaybackControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration

/** Library screen state. */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(
        /** The most recently listened book that is ready, shown as "Continue listening". */
        val continueListening: LibraryBook?,
        val books: List<LibraryBook>,
        /** Estimated listening time left by book id, for books that are ready and not finished. */
        val timeLeft: Map<String, Duration> = emptyMap(),
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val repository: LibraryRepository,
        private val playback: PlaybackControls,
        settings: SettingsRepository,
        rates: SpeakingRateTracker,
    ) : ViewModel() {
        private val preferences =
            settings.settings.catch { e ->
                if (e !is IOException) throw e
                Diagnostics.recordHandled("read settings for the library", e)
                emit(AppSettings())
            }

        val state: StateFlow<LibraryUiState> =
            combine(repository.books, preferences, rates.rates) { books, prefs, learned ->
                LibraryUiState.Content(
                    continueListening = books.firstOrNull { it.status == BookStatus.READY && it.started },
                    books = books,
                    timeLeft =
                        books
                            .mapNotNull { book -> LibraryTimeLeft.of(book, prefs, learned)?.let { book.id to it } }
                            .toMap(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

        /** Starts or resumes listening to a book that is ready. */
        fun play(bookId: String) = playback.play(bookId)

        fun remove(bookId: String) {
            viewModelScope.launch { repository.remove(bookId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
