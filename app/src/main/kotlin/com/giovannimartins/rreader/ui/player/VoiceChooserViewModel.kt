package com.giovannimartins.rreader.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The player's voice chooser: the voices the open book can be read with, and the choice of one, which takes effect at
 * the sentence being read ([PlaybackControls.refreshVoice]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VoiceChooserViewModel
    @Inject
    constructor(
        private val controls: PlaybackControls,
        private val choices: VoiceChoices,
    ) : ViewModel() {
        /** The voices for the open book; empty while no book is open. */
        val menu: StateFlow<VoiceMenu> =
            controls.nowPlaying
                .map { it?.bookId }
                .distinctUntilChanged()
                .flatMapLatest { bookId -> bookId?.let(choices::forBook) ?: flowOf(VoiceMenu()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VoiceMenu())

        /** Reads the open book with [voiceId], for this book only or for every book ([forAllBooks]). */
        fun choose(
            voiceId: String,
            forAllBooks: Boolean,
        ) {
            val bookId = controls.nowPlaying.value?.bookId ?: return
            viewModelScope.launch {
                choices.choose(bookId, voiceId, forAllBooks)
                controls.refreshVoice()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
