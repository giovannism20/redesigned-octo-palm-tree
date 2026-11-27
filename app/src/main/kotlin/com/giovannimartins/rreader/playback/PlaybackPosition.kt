package com.giovannimartins.rreader.playback

import androidx.annotation.OptIn
import androidx.media3.common.SimpleBasePlayer.LivePositionSupplier
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.util.UnstableApi
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Where the facade player is in the book. While the internal player is on a queued segment, the position is
 * that segment's start plus the player's progress through it, scaled to the segment's span, so it advances
 * smoothly and reaches the next segment's start exactly. Otherwise it is the [anchor]: a seek target, the
 * resume point or the end of the book.
 */
@OptIn(UnstableApi::class)
internal class PlaybackPosition(
    private val feeder: SegmentFeeder,
) {
    var anchor: ReadingPosition = ReadingPosition.START

    /** Media time scale of the chapters; changed only at chapter changes and seeks, when positions jump anyway. */
    var clock: ChapterClock = ChapterClock(SpeakingRate.DEFAULT)

    private var live: LivePositionSupplier? = null
    private var liveKey: String? = null

    /** The reading position now. */
    fun current(): ReadingPosition =
        feeder.current()?.let { segment ->
            val offset = segment.range.start + (feeder.progressIn(segment) * segment.spanLength).roundToInt()
            ReadingPosition(segment.chapterIndex, offset)
        } ?: anchor

    /** Index of the chapter being listened to. */
    fun chapter(): Int = feeder.current()?.chapterIndex ?: anchor.chapterIndex

    /** End of the span the listener is in, when known; forward seeks skip past it. */
    fun spanEnd(plans: ChapterPlans): Int? = feeder.current()?.spanEnd ?: plans.spanEndAt(anchor)

    /**
     * The facade's position in the current chapter: one live supplier per queued segment, reading the internal
     * player, or a constant at the [anchor]. Keeping the same supplier while a segment plays lets the base
     * player tell continuous progress from jumps.
     */
    fun supplier(): PositionSupplier {
        val segment = feeder.current()
        return when {
            segment == null -> {
                freeze()
                PositionSupplier.getConstant(clock.millisFor(anchor.charOffset))
            }

            segment.key == liveKey -> {
                checkNotNull(live)
            }

            else -> {
                freeze()
                LivePositionSupplier { millisIn(segment) }.also {
                    live = it
                    liveKey = segment.key
                }
            }
        }
    }

    /** The facade's position in the current chapter now, in milliseconds. */
    fun millis(): Long = supplier().get()

    /**
     * Disconnects the live supplier at its current value. Called before the internal player leaves the segment
     * for another reason than reaching its end, so earlier states keep the position they reported.
     */
    fun freeze() {
        live?.let { it.disconnect(it.get()) }
        live = null
        liveKey = null
    }

    /** Media time of chapter [chapterIndex], [chapterLength] characters long, up to which audio is rendered. */
    fun bufferedMillis(
        chapterIndex: Int,
        chapterLength: Int,
    ): Long {
        val last = feeder.lastQueued
        return when {
            last == null || last.chapterIndex < chapterIndex -> millis()
            last.chapterIndex > chapterIndex -> clock.millisFor(chapterLength)
            else -> clock.millisFor(last.spanEnd)
        }
    }

    private fun millisIn(segment: QueuedSegment): Long =
        clock.millisFor(segment.range.start) +
            (feeder.progressIn(segment) * clock.millisFor(segment.spanLength)).roundToLong()
}
