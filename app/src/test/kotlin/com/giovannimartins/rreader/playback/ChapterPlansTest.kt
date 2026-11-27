package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterPlansTest {
    private val planner = SegmentPlanner()
    private val texts = listOf(chapterText(0, 3), chapterText(1, 2), chapterText(2, 2))

    @Test
    fun `locates the segment that holds a position, or the next one in a gap`() =
        runTest {
            val plans = plans()
            val segments = planner.plan(texts[0])

            val inside = plans.locate(ReadingPosition(0, segments[1].start + 3))
            val inGap = plans.locate(ReadingPosition(0, segments[0].end))

            assertThat(inside?.position).isEqualTo(ReadingPosition(0, segments[1].start))
            assertThat(inGap?.position).isEqualTo(ReadingPosition(0, segments[1].start))
            assertThat(inside?.text()).isEqualTo(texts[0].substring(segments[1].start, segments[1].end))
        }

    @Test
    fun `a position past the end of a chapter moves to the next chapter, and the end of the book to null`() =
        runTest {
            val plans = plans()

            assertThat(plans.locate(ReadingPosition(0, texts[0].length))?.position).isEqualTo(ReadingPosition(1, 0))
            assertThat(plans.locate(ReadingPosition(2, texts[2].length))).isNull()
        }

    @Test
    fun `walks every segment of the book in order across chapters`() =
        runTest {
            val plans = plans()
            val visited = mutableListOf<ReadingPosition>()

            var cursor = plans.locate(ReadingPosition.START)
            while (cursor != null) {
                visited += cursor.position
                cursor = plans.after(cursor)
            }

            val expected =
                texts.flatMapIndexed { index, text -> planner.plan(text).map { ReadingPosition(index, it.start) } }
            assertThat(visited).containsExactlyElementsIn(expected).inOrder()
        }

    @Test
    fun `a rewound start is the segment before, across chapters, but not before the book's first segment`() =
        runTest {
            val plans = plans()
            val first = planner.plan(texts[0])
            val second = planner.plan(texts[1])
            val rewind = 1_000

            val inChapter = plans.locate(ReadingPosition(0, first[2].start + 4), rewind)
            val acrossChapters = plans.locate(ReadingPosition(1, 3), rewind)
            val atStart = plans.locate(ReadingPosition(0, 5), rewind)
            val short = plans.locate(ReadingPosition(1, second[1].start + 10), rewindChars = 5)

            assertThat(inChapter?.position).isEqualTo(ReadingPosition(0, first[1].start))
            assertThat(acrossChapters?.position).isEqualTo(ReadingPosition(0, first.last().start))
            assertThat(atStart?.position).isEqualTo(ReadingPosition.START)
            // Five characters back stay inside the segment, whose start is where playback resumes.
            assertThat(short?.position).isEqualTo(ReadingPosition(1, second[1].start))
        }

    @Test
    fun `spans reach the next segment so positions have no gaps`() =
        runTest {
            val plans = plans()
            val segments = planner.plan(texts[0])
            plans.locate(ReadingPosition.START)

            assertThat(plans.spanEndAt(ReadingPosition(0, 0))).isEqualTo(segments[1].start)
            assertThat(plans.spanEndAt(ReadingPosition(0, segments.last().start))).isEqualTo(texts[0].length)
            assertThat(plans.spanEndAt(ReadingPosition(1, 0))).isNull()
        }

    @Test
    fun `the first segment is a heading only when it repeats the chapter title`() =
        runTest {
            val heading = "Capítulo 1\n\n${chapterText(0, 1)}"
            val content = FakePlaybackContent(book(listOf(heading)), listOf(heading))
            val plans = ChapterPlans(content, planner, UnconfinedTestDispatcher(testScheduler))
            plans.reset(content.book)

            val first = plans.locate(ReadingPosition.START)!!

            assertThat(first.isHeading()).isTrue()
            assertThat(plans.after(first)!!.isHeading()).isFalse()
        }

    private fun TestScope.plans(): ChapterPlans {
        val content = FakePlaybackContent(book(texts), texts)
        return ChapterPlans(content, planner, UnconfinedTestDispatcher(testScheduler)).apply { reset(content.book) }
    }
}
