package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import com.google.common.truth.Truth.assertThat

/**
 * Draws synthetic pages the way a typeset book prints them: a 400 x 600 page with one column from x = 50 to x = 350,
 * body text of size 10 on a 12-unit line pitch starting at baseline 100, and characters 5 units wide. Running
 * headers sit at baseline 40 and footers at baseline 580, inside the 10% margin bands.
 */
internal class PageSketch(
    private val index: Int,
) {
    private val lines = mutableListOf<LayoutLine>()
    private var baseline = BODY_TOP

    /** A justified line that fills the column, optionally with a first-line indent. */
    fun full(
        text: String,
        indent: Boolean = false,
    ) = line(text, left = if (indent) LEFT + INDENT else LEFT, right = RIGHT)

    /** The last line of a paragraph: it ends where its text ends. */
    fun last(
        text: String,
        indent: Boolean = false,
    ) {
        val left = if (indent) LEFT + INDENT else LEFT
        line(text, left = left, right = left + text.length * CHAR_WIDTH)
    }

    /** A paragraph: first line indented, full lines in the middle, a short last line. */
    fun paragraph(vararg text: String) {
        text.forEachIndexed { position, line ->
            if (position == text.lastIndex) last(line, indent = position == 0) else full(line, indent = position == 0)
        }
    }

    /** A realistic three-line paragraph that opens with [opening]; [storyText] is its text. */
    fun story(opening: String) = paragraph(opening, STORY_MIDDLE, STORY_END)

    /** A line centered in the column, as headings often are. */
    fun centered(
        text: String,
        size: Float = SIZE,
    ) {
        val width = text.length * CHAR_WIDTH * size / SIZE
        val left = (LEFT + RIGHT - width) / 2
        line(text, left = left, right = left + width, size = size)
    }

    /** Empty space of [lines] body lines. */
    fun gap(lines: Float = 1f) {
        baseline += PITCH * lines
    }

    fun header(
        text: String,
        left: Float = LEFT,
    ) = place(text, left, HEADER_BASELINE)

    fun footer(
        text: String,
        left: Float = (LEFT + RIGHT) / 2,
    ) = place(text, left, FOOTER_BASELINE)

    /** A line at the current position; the next one goes one pitch (scaled by [size]) below. */
    fun line(
        text: String,
        left: Float = LEFT,
        right: Float = RIGHT,
        size: Float = SIZE,
    ) {
        if (size != SIZE) baseline += PITCH * (size / SIZE - 1)
        lines += box(text, left, right, baseline, size)
        baseline += PITCH
    }

    fun build(): PageLayout = PageLayout(index, WIDTH, HEIGHT, lines.toList())

    private fun place(
        text: String,
        left: Float,
        at: Float,
    ) {
        lines += box(text, left, left + text.length * CHAR_WIDTH, at, SIZE)
    }

    private fun box(
        text: String,
        left: Float,
        right: Float,
        at: Float,
        size: Float,
    ) = LayoutLine(text, left, at - size * ASCENT, right, at, size)

    companion object {
        const val WIDTH = 400f
        const val HEIGHT = 600f
        const val LEFT = 50f
        const val RIGHT = 350f
        const val SIZE = 10f
        const val PITCH = 12f
        const val CHAR_WIDTH = 5f
        const val INDENT = 15f
        const val BODY_TOP = 100f
        const val HEADER_BASELINE = 40f
        const val FOOTER_BASELINE = 580f
        const val STORY_MIDDLE = "e segue por uma linha cheia"
        const val STORY_END = "até o fim."
        private const val ASCENT = 0.8f
    }
}

/** A page drawn by [block]. */
internal fun page(
    index: Int,
    block: PageSketch.() -> Unit,
): PageLayout = PageSketch(index).apply(block).build()

/** The text of [PageSketch.story] paragraph that opens with [opening]. */
internal fun storyText(opening: String): String = "$opening ${PageSketch.STORY_MIDDLE} ${PageSketch.STORY_END}"

/** Builds the book of [pages], failing the test when it has no text. */
internal fun buildBook(
    pages: List<PageLayout>,
    outline: List<OutlineEntry> = emptyList(),
): NormalizedBook {
    val result = LayoutBookBuilder().build(BookMetadata("Livro"), pages, outline)
    assertThat(result).isInstanceOf(LayoutResult.Success::class.java)
    return (result as LayoutResult.Success).book
}

/** The paragraphs of every chapter of the book built from [pages]. */
internal fun paragraphsOf(pages: List<PageLayout>): List<String> =
    buildBook(pages).chapters.flatMap { chapter -> chapter.text.split("\n\n") }

/** The paragraphs of every chapter of the book built from [pages]. */
internal fun paragraphsOf(vararg pages: PageLayout): List<String> = paragraphsOf(pages.toList())
