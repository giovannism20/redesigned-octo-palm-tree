package com.giovannimartins.rreader.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.importer.BookIntake
import com.giovannimartins.rreader.importer.IntakeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the import UI shows. [message] is shown once and then cleared with [ImportViewModel.messageShown]. */
data class ImportUiState(
    val receiving: Boolean = false,
    val message: ImportMessage? = null,
)

/** One-off feedback after handing a file to the app. */
sealed interface ImportMessage {
    data object Queued : ImportMessage

    data object Duplicate : ImportMessage

    data class Rejected(
        val reason: ImportFailure,
    ) : ImportMessage
}

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val intake: BookIntake,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ImportUiState())
        val state: StateFlow<ImportUiState> = _state.asStateFlow()

        /** Copies the file behind [uri] into the library and queues its preparation. */
        fun import(uri: Uri) {
            _state.update { it.copy(receiving = true) }
            viewModelScope.launch {
                val message =
                    when (val result = intake.import(uri)) {
                        is IntakeResult.Queued -> ImportMessage.Queued
                        is IntakeResult.Duplicate -> ImportMessage.Duplicate
                        is IntakeResult.Rejected -> ImportMessage.Rejected(result.reason)
                    }
                _state.update { ImportUiState(receiving = false, message = message) }
            }
        }

        fun messageShown() = _state.update { it.copy(message = null) }
    }
