package com.giovannimartins.rreader.ui.library

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.importer.ImportProgress
import com.giovannimartins.rreader.ui.assertAllTextReadableIn
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.theme.RReaderDarkColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderLightColorScheme
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours

/** On a phone-sized screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

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

    @Test
    fun `a large action at the top adds a book`() {
        show(ready("Dom Casmurro"))

        val add = compose.onNode(hasText("Add a book") and hasClickAction())
        val top = add.fetchSemanticsNode().boundsInRoot.top
        val firstBook =
            compose
                .onNodeWithText("Continue listening")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertThat(top).isLessThan(firstBook)
        add.assertHeightIsAtLeast(56.dp).performClick()

        assertThat(events).containsExactly("add")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h891dp")
    fun `an empty library offers to add a book once, in Portuguese`() {
        show()

        compose.onNodeWithText("Sua biblioteca está vazia").assertExists()
        compose.onNode(hasText("Adicionar livro") and hasClickAction()).performClick()
        assertThat(countAddButtons()).isEqualTo(1)
        assertThat(events).containsExactly("add")
    }

    @Test
    fun `all text is readable in the dark theme`() {
        show(ready("Dom Casmurro"), preparing("Livro", progress = null), darkTheme = true)

        compose.assertAllTextReadableIn(RReaderDarkColorScheme)
    }

    @Test
    fun `all text is readable in the light theme`() {
        show(ready("Dom Casmurro"), preparing("Livro", progress = null), darkTheme = false)

        compose.assertAllTextReadableIn(RReaderLightColorScheme)
    }

    @Test
    fun `the continue card pauses the book while it plays, and listens otherwise`() {
        show(ready("Helena"), playingBookId = "Helena")

        compose.onNode(hasText("Pause") and hasClickAction()).performClick()
        assertThat(events).containsExactly("pause")
    }

    @Test
    fun `the continue card offers to listen while its book does not play`() {
        show(ready("Helena"), playingBookId = "Iaiá Garcia")

        compose.onNode(hasText("Listen") and hasClickAction()).performClick()
        assertThat(events).containsExactly("play Helena")
    }

    private fun countAddButtons() =
        compose.onAllNodes(hasText("Adicionar livro") and hasClickAction()).fetchSemanticsNodes().size

    private fun show(
        vararg books: LibraryBook,
        darkTheme: Boolean = true,
        playingBookId: String? = null,
    ) {
        val started = books.firstOrNull { it.status == BookStatus.READY }
        compose.setContent {
            RReaderTheme(darkTheme = darkTheme) {
                ScreenSurface {
                    LibraryScreen(
                        state =
                            LibraryUiState.Content(
                                continueListening = started,
                                books = books.toList(),
                                timeLeft = started?.let { mapOf(it.id to 3.hours) }.orEmpty(),
                                playingBookId = playingBookId,
                            ),
                        onAddBook = { events += "add" },
                        onPlay = { events += "play $it" },
                        onPause = { events += "pause" },
                        onRemove = {},
                        onRetry = {},
                    )
                }
            }
        }
    }

    private fun ready(title: String) =
        LibraryBook(
            id = title,
            title = title,
            authors = listOf("Machado de Assis"),
            status = BookStatus.READY,
            failure = null,
            coverFile = null,
            charsRead = 40,
            totalChars = 100,
        )

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
