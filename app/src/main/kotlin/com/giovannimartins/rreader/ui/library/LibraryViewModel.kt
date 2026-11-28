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
import com.giovannimartins.rreader.tts.SpeakingRates
import com.giovannimartins.rreader.ui.player.NowPlaying
import com.giovannimartins.rreader.ui.player.PlaybackControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration

/** Library screen state. */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** The library could not be read; the listener may try again. */
    data object Unavailable : LibraryUiState

    data class Content(
        /** The most recently listened book that is ready, shown as "Continue listening". */
        val continueListening: LibraryBook?,
        val books: List<LibraryBook>,
        /** Estimated listening time left by book id, for books that are ready and not finished. */
        val timeLeft: Map<String, Duration> = emptyMap(),
        /** The book being listened to right now, or null when nothing plays. */
        val playingBookId: String? = null,
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

        /** Asks to read the library again after it could not be read. */
        private val retries = Channel<Unit>(Channel.CONFLATED)

        /**
         * The library, or [LibraryUiState.Unavailable] when it cannot be read (for example a database row too large
         * for a cursor), which the screen explains instead of the app closing; [retry] reads it again.
         */
        val state: StateFlow<LibraryUiState> =
            combine<List<LibraryBook>, AppSettings, SpeakingRates, String?, LibraryUiState>(
                repository.books,
                preferences,
                rates.rates,
                playback.nowPlaying.map { it?.takeIf(NowPlaying::isPlaying)?.bookId }.distinctUntilChanged(),
            ) { books, prefs, learned, playing ->
                LibraryUiState.Content(
                    playingBookId = playing,
                    continueListening = books.firstOrNull { it.status == BookStatus.READY && it.started },
                    books = books,
                    timeLeft =
                        books
                            .mapNotNull { book -> LibraryTimeLeft.of(book, prefs, learned)?.let { book.id to it } }
                            .toMap(),
                )
            }.retryWhen { cause, _ ->
                Diagnostics.recordHandled("read the library", cause)
                retries.tryReceive() // A request made before this failure does not count.
                emit(LibraryUiState.Unavailable)
                retries.receive()
                true
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

        /** Starts or resumes listening to a book that is ready. */
        fun play(bookId: String) = playback.play(bookId)

        /** Pauses the book being listened to. */
        fun pause() = playback.pause()

        /** Reads the library again after it was [LibraryUiState.Unavailable]. */
        fun retry() {
            retries.trySend(Unit)
        }

        /** Removes a book; the player closes it first when it is the open book. */
        fun remove(bookId: String) {
            playback.closeBook(bookId)
            viewModelScope.launch { repository.remove(bookId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
