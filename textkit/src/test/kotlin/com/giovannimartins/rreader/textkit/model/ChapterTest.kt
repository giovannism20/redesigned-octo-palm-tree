package com.giovannimartins.rreader.textkit.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChapterTest {
    @Test
    fun `builds text from paragraphs and exposes their ranges`() {
        val chapter = Chapter.fromParagraphs("I", listOf("  Primeiro parágrafo. ", "", "Segundo."))

        assertThat(chapter.text).isEqualTo("Primeiro parágrafo.\n\nSegundo.")
        assertThat(chapter.paragraphRanges()).containsExactly(TextRange(0, 19), TextRange(21, 29)).inOrder()
        assertThat(chapter.text.substring(21, 29)).isEqualTo("Segundo.")
    }

    @Test
    fun `rejects blank chapters and empty books`() {
        assertThrows<IllegalArgumentException> { Chapter(null, "   ") }
        assertThrows<IllegalArgumentException> { NormalizedBook(BookMetadata("Livro"), emptyList()) }
    }

    @Test
    fun `orders reading positions by chapter then offset`() {
        val positions = listOf(ReadingPosition(1, 0), ReadingPosition(0, 50), ReadingPosition(0, 10))

        assertThat(positions.sorted())
            .containsExactly(ReadingPosition(0, 10), ReadingPosition(0, 50), ReadingPosition(1, 0))
            .inOrder()
    }

    @Test
    fun `counts the total length of a book`() {
        val book = NormalizedBook(BookMetadata("Livro"), listOf(Chapter(null, "abc"), Chapter("2", "de")))

        assertThat(book.totalLength).isEqualTo(5)
    }
}
