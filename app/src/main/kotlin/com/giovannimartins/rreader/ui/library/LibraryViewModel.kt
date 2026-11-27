package com.giovannimartins.rreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.ui.player.PlaybackControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Library screen state. */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(
        /** The most recently listened book that is ready, shown as "Continue listening". */
        val continueListening: LibraryBook?,
        val books: List<LibraryBook>,
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val repository: LibraryRepository,
        private val playback: PlaybackControls,
    ) : ViewModel() {
        val state: StateFlow<LibraryUiState> =
            repository.books
                .map { books ->
                    LibraryUiState.Content(
                        continueListening = books.firstOrNull { it.status == BookStatus.READY && it.started },
                        books = books,
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
