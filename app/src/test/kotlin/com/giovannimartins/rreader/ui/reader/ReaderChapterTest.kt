package com.giovannimartins.rreader.ui.reader

import com.giovannimartins.rreader.playback.BOOK_ID
import com.giovannimartins.rreader.playback.SpokenSegment
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderChapterTest {
    private val planner = SegmentPlanner()
    private val text =
        "Capítulo I\n\n" +
            "Primeira frase do parágrafo um, que é bem comprida. Segunda frase do mesmo parágrafo um.\n\n" +
            "Terceira frase, agora no parágrafo dois, também longa."
    private val segments = planner.plan(text)
    private val chapter = ReaderChapter(ChapterKey(BOOK_ID, 3), text, planner)

    @Test
    fun `lays the chapter out in its paragraphs`() {
        assertThat(chapter.paragraphs)
            .containsExactly(
                "Capítulo I",
                "Primeira frase do parágrafo um, que é bem comprida. Segunda frase do mesmo parágrafo um.",
                "Terceira frase, agora no parágrafo dois, também longa.",
            ).inOrder()
    }

    @Test
    fun `highlights the segment being spoken inside its paragraph`() {
        val second = segments[2]

        val highlight = checkNotNull(chapter.highlightOf(SpokenSegment(BOOK_ID, 3, second)))

        assertThat(highlight.paragraph).isEqualTo(1)
        assertThat(chapter.paragraphs[1].substring(highlight.start, highlight.end))
            .isEqualTo("Segunda frase do mesmo parágrafo um.")
    }

    @Test
    fun `segments of another chapter or book are not highlighted`() {
        val other = "00000000-0000-0000-0000-00000000000f"

        assertThat(chapter.highlightOf(SpokenSegment(BOOK_ID, 2, segments[1]))).isNull()
        assertThat(chapter.highlightOf(SpokenSegment(other, 3, segments[1]))).isNull()
        assertThat(chapter.highlightOf(null)).isNull()
    }

    @Test
    fun `a tap reads from the start of the sentence under the finger`() {
        val paragraph = chapter.paragraphs[1]

        val inSecond = chapter.readingPositionAt(1, paragraph.indexOf("mesmo"))
        val inFirst = chapter.readingPositionAt(1, paragraph.indexOf("comprida"))

        assertThat(inSecond).isEqualTo(ReadingPosition(3, segments[2].start))
        assertThat(inFirst).isEqualTo(ReadingPosition(3, segments[1].start))
    }

    @Test
    fun `a tap between sentences reads from the next one, and past the end from the last`() {
        val paragraph = chapter.paragraphs[1]

        assertThat(
            chapter.readingPositionAt(1, paragraph.indexOf(" Segunda")),
        ).isEqualTo(ReadingPosition(3, segments[2].start))
        assertThat(chapter.readingPositionAt(2, 10_000)).isEqualTo(ReadingPosition(3, segments.last().start))
        assertThat(chapter.readingPositionAt(7, 0)).isNull()
    }
}
