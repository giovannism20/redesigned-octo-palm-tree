package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SentenceSegmenter
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    abstract fun playbackContent(repository: PlaybackRepository): PlaybackContent

    companion object {
        /** Sentences grouped into segments with the textkit defaults (ADR 0003). */
        @Provides
        fun segmentPlanner(): SegmentPlanner =
            SegmentPlanner(
                segmenter = SentenceSegmenter(),
                minSegmentLength = SegmentPlanner.DEFAULT_MIN_SEGMENT_LENGTH,
                maxSegmentLength = SegmentPlanner.DEFAULT_MAX_SEGMENT_LENGTH,
            )

        @Provides
        fun speechNormalizer(): SpeechNormalizer = SpeechNormalizer()
    }
}
