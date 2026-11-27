package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SegmentQueueTest {
    private val queue = SegmentQueue()

    @Test
    fun `lookahead counts the segments queued after the playing one`() {
        val segments = (0 until 4).map { segment(it, durationMillis = 10_000) }
        segments.forEach(queue::add)

        assertThat(queue.lookahead(segments[1].key)).isEqualTo(Lookahead(2, 20_000))
        assertThat(queue.lookahead(null)).isEqualTo(Lookahead(4, 40_000))
        assertThat(queue.lookahead(segments.last().key)).isEqualTo(Lookahead(0, 0))
    }

    @Test
    fun `finds segments by media id and drops played ones`() {
        val segments = (0 until 3).map { segment(it) }
        segments.forEach(queue::add)

        queue.dropFirst(2)

        assertThat(queue.find(segments[0].key)).isNull()
        assertThat(queue.find(segments[2].key)).isEqualTo(segments[2])
        assertThat(queue.last()).isEqualTo(segments[2])
        assertThat(queue.find(null)).isNull()
    }

    @Test
    fun `segments of different chapters have different media ids`() {
        assertThat(segment(0, chapter = 0).key).isNotEqualTo(segment(0, chapter = 1).key)
    }

    @Test
    fun `renders ahead until two minutes and four segments are queued`() {
        assertThat(RenderAhead.wantsMore(Lookahead(segments = 3, durationMillis = 600_000))).isTrue()
        assertThat(RenderAhead.wantsMore(Lookahead(segments = 30, durationMillis = 119_000))).isTrue()
        assertThat(RenderAhead.wantsMore(Lookahead(segments = 4, durationMillis = 120_000))).isFalse()
    }

    @Test
    fun `a segment's span runs to the next segment`() {
        val segment = QueuedSegment(0, TextRange(10, 40), spanEnd = 42, file = File("x.wav"), durationMillis = 1_000)

        assertThat(segment.spanLength).isEqualTo(32)
    }

    private fun segment(
        index: Int,
        chapter: Int = 0,
        durationMillis: Long = 1_000,
    ) = QueuedSegment(
        chapterIndex = chapter,
        range = TextRange(index * 10, index * 10 + 9),
        spanEnd = index * 10 + 10,
        file = File("$index.wav"),
        durationMillis = durationMillis,
    )
}
