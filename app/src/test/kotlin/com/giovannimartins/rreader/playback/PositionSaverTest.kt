package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PositionSaverTest {
    private var now = 0L
    private val written = mutableListOf<ReadingPosition>()
    private val saver =
        PositionSaver(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            elapsedMillis = { now },
            write = { _, position -> written += position },
            throttle = 5.seconds,
        )

    @Test
    fun `forced saves always write`() {
        saver.save(BOOK_ID, ReadingPosition(0, 10), force = true)
        saver.save(BOOK_ID, ReadingPosition(0, 20), force = true)

        assertThat(written).containsExactly(ReadingPosition(0, 10), ReadingPosition(0, 20)).inOrder()
    }

    @Test
    fun `other saves write at most once per throttle window`() {
        saver.save(BOOK_ID, ReadingPosition(0, 10), force = false)
        now = 1_000
        saver.save(BOOK_ID, ReadingPosition(0, 20), force = false)
        now = 6_000
        saver.save(BOOK_ID, ReadingPosition(0, 30), force = false)

        assertThat(written).containsExactly(ReadingPosition(0, 10), ReadingPosition(0, 30)).inOrder()
    }

    @Test
    fun `an unchanged position is not written again`() {
        saver.save(BOOK_ID, ReadingPosition(1, 5), force = true)
        saver.save(BOOK_ID, ReadingPosition(1, 5), force = true)

        assertThat(written).containsExactly(ReadingPosition(1, 5))
    }

    @Test
    fun `nothing is written after closing`() {
        saver.close()
        saver.save(BOOK_ID, ReadingPosition(1, 5), force = true)

        assertThat(written).isEmpty()
    }
}
