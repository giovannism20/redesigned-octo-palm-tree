package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SegmentPlannerTest {
    private val planner = SegmentPlanner()

    /** Plans [text], checks the range contract and returns the segments as strings. */
    private fun segments(
        text: String,
        using: SegmentPlanner = planner,
        maxLength: Int = SegmentPlanner.DEFAULT_MAX_SEGMENT_LENGTH,
    ): List<String> {
        val ranges = using.plan(text)
        assertWellFormed(text, ranges, maxLength)
        return text.slices(ranges)
    }

    @Nested
    inner class Plan {
        @Test
        fun `merges short sentences with the following ones in the same paragraph`() {
            assertThat(segments("— Sim. — Não. — Talvez. Ele saiu de casa cedo naquela manhã."))
                .containsExactly("— Sim. — Não. — Talvez. Ele saiu de casa cedo naquela manhã.")
        }

        @Test
        fun `keeps sentences that are long enough on their own`() {
            assertThat(segments("Ele saiu de casa cedo naquela manhã. Voltou tarde. Estava cansado."))
                .containsExactly("Ele saiu de casa cedo naquela manhã.", "Voltou tarde. Estava cansado.")
                .inOrder()
        }

        @Test
        fun `keeps a short last sentence of a paragraph on its own`() {
            assertThat(segments("Ele saiu de casa cedo naquela manhã. Fim.\n\nNo dia seguinte, voltou."))
                .containsExactly("Ele saiu de casa cedo naquela manhã.", "Fim.", "No dia seguinte, voltou.")
                .inOrder()
        }

        @Test
        fun `never merges across paragraphs`() {
            assertThat(segments("Sim.\n\nEle saiu de casa cedo naquela manhã."))
                .containsExactly("Sim.", "Ele saiu de casa cedo naquela manhã.")
                .inOrder()
        }

        @Test
        fun `merges short lines of the same paragraph`() {
            assertThat(segments("Sim, senhor,\ndisse ele baixinho."))
                .containsExactly("Sim, senhor,\ndisse ele baixinho.")
        }

        @Test
        fun `never merges beyond the maximum length`() {
            val small = SegmentPlanner(SentenceSegmenter(maxSentenceLength = 40), maxSegmentLength = 40)

            assertThat(segments("Sim. Ele andou pela praia deserta e olhou.", small, maxLength = 40))
                .containsExactly("Sim.", "Ele andou pela praia deserta e olhou.")
                .inOrder()
        }

        @Test
        fun `turns every sentence into a segment when the minimum is zero`() {
            val eager = SegmentPlanner(minSegmentLength = 0)

            assertThat(segments("Sim. Não. Talvez.", eager)).containsExactly("Sim.", "Não.", "Talvez.").inOrder()
        }

        @Test
        fun `returns nothing for blank text`() {
            assertThat(planner.plan(" \n\n ")).isEmpty()
        }

        @Test
        fun `rejects inconsistent limits`() {
            assertThrows<IllegalArgumentException> { SegmentPlanner(minSegmentLength = -1) }
            assertThrows<IllegalArgumentException> { SegmentPlanner(maxSegmentLength = 100) }
        }

        @Test
        fun `builds segments from whole sentences, merging only when a segment is short`() {
            val segmenter = SentenceSegmenter()

            for (text in PortugueseSamples.TEXTS) {
                val sentences = segmenter.split(text)
                val segments = planner.plan(text)

                assertWellFormed(text, segments, SegmentPlanner.DEFAULT_MAX_SEGMENT_LENGTH)
                assertThat(sentences.map { it.start }).containsAtLeastElementsIn(segments.map { it.start })
                assertThat(sentences.map { it.end }).containsAtLeastElementsIn(segments.map { it.end })
                segments.zipWithNext().forEach { (segment, next) ->
                    val sameParagraph = !text.substring(segment.end, next.start).contains(Chapter.PARAGRAPH_SEPARATOR)
                    val fits = next.end - segment.start <= SegmentPlanner.DEFAULT_MAX_SEGMENT_LENGTH
                    val short = segment.length < SegmentPlanner.DEFAULT_MIN_SEGMENT_LENGTH
                    assertWithMessage("%s before %s in <%s>", segment, next, text)
                        .that(short && sameParagraph && fits)
                        .isFalse()
                }
            }
        }
    }

    @Nested
    inner class SegmentAt {
        private val ranges = listOf(TextRange(0, 10), TextRange(12, 20), TextRange(22, 30))

        @Test
        fun `finds the segment containing an offset`() {
            assertThat(planner.segmentAt(ranges, 0)).isEqualTo(0)
            assertThat(planner.segmentAt(ranges, 9)).isEqualTo(0)
            assertThat(planner.segmentAt(ranges, 12)).isEqualTo(1)
            assertThat(planner.segmentAt(ranges, 19)).isEqualTo(1)
            assertThat(planner.segmentAt(ranges, 29)).isEqualTo(2)
        }

        @Test
        fun `picks the following segment for an offset between segments`() {
            assertThat(planner.segmentAt(ranges, 10)).isEqualTo(1)
            assertThat(planner.segmentAt(ranges, 11)).isEqualTo(1)
            assertThat(planner.segmentAt(ranges, 21)).isEqualTo(2)
            assertThat(planner.segmentAt(listOf(TextRange(5, 10)), 2)).isEqualTo(0)
        }

        @Test
        fun `reports the end of the chapter after the last segment`() {
            assertThat(planner.segmentAt(ranges, 30)).isEqualTo(ranges.size)
            assertThat(planner.segmentAt(ranges, 500)).isEqualTo(ranges.size)
            assertThat(planner.segmentAt(emptyList(), 0)).isEqualTo(0)
        }

        @Test
        fun `resumes at the start of the sentence that holds the position`() {
            val text = "Primeira frase, longa o bastante para ficar só. Segunda frase, longa o bastante também."
            val segments = planner.plan(text)
            val middleOfSecond = text.indexOf("bastante também")

            val resumed = segments[planner.segmentAt(segments, middleOfSecond)]

            assertThat(resumed.start).isEqualTo(text.indexOf("Segunda"))
        }

        @Test
        fun `rejects a negative offset`() {
            assertThrows<IllegalArgumentException> { planner.segmentAt(ranges, -1) }
        }
    }
}
