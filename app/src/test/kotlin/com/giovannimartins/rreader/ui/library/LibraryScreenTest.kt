package com.giovannimartins.rreader.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.importer.ImportProgress
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a phone-sized screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a scanned book being prepared shows the page text recognition is reading`() {
        show(preparing("Livro escaneado", ImportProgress.RecognizingText(12, 300)))

        compose.onNodeWithText("Recognizing text: page 12 of 300").assertExists()
        compose.onNodeWithText("Preparing the book…").assertDoesNotExist()
    }

    @Test
    fun `a book being prepared without progress is just being prepared`() {
        show(preparing("Livro", progress = null))

        compose.onNodeWithText("Preparing the book…").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h891dp")
    fun `the progress speaks Portuguese`() {
        show(preparing("Livro escaneado", ImportProgress.RecognizingText(3, 10)))

        compose.onNodeWithText("Reconhecendo texto: página 3 de 10").assertExists()
    }

    private fun show(vararg books: LibraryBook) {
        compose.setContent {
            RReaderTheme {
                LibraryScreen(
                    state = LibraryUiState.Content(continueListening = null, books = books.toList()),
                    onAddBook = {},
                    onPlay = {},
                    onRemove = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun preparing(
        title: String,
        progress: ImportProgress?,
    ) = LibraryBook(
        id = title,
        title = title,
        authors = emptyList(),
        status = BookStatus.IMPORTING,
        failure = null,
        coverFile = null,
        charsRead = 0,
        totalChars = 0,
        importProgress = progress,
    )
}
