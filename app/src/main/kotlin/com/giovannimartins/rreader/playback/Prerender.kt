package com.giovannimartins.rreader.playback

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.DefaultDispatcher
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.SegmentAudio
import com.giovannimartins.rreader.tts.SegmentRenderer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Asks for a book to be rendered ahead of listening while the phone charges. */
fun interface PrerenderJobs {
    /** Renders ahead in book [bookId] the next time the phone charges, instead of any book asked for before. */
    fun schedule(bookId: String)
}

/**
 * [PrerenderJobs] on WorkManager: one job for the whole app ([UNIQUE_NAME], the latest book replaces the one before),
 * which runs only while the phone charges, with enough battery and storage, and stops when it is unplugged. Its name
 * and tags say nothing about the book (SR-8).
 */
class WorkManagerPrerenderJobs
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : PrerenderJobs {
        override fun schedule(bookId: String) {
            val request =
                OneTimeWorkRequestBuilder<PrerenderWorker>()
                    .setInputData(workDataOf(PrerenderWorker.BOOK_ID to bookId))
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiresCharging(true)
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        companion object {
            const val UNIQUE_NAME = "prerender"
        }
    }

/** What [Prerenderer.prerender] did. */
sealed interface PrerenderOutcome {
    /** The book's voice keeps up with listening, or there is no book or voice: nothing to render ahead. */
    data object NotNeeded : PrerenderOutcome

    /** [millis] of audio after the reading position are in the cache, rendered now or before. */
    data class Ready(
        val millis: Long,
    ) : PrerenderOutcome

    /** The voice failed; listening will render what is missing. */
    data object Failed : PrerenderOutcome
}

/**
 * Renders the segments after a book's saved reading position into the audio cache, for voices that may be slower
 * than real time on a phone (Kokoro, ADR 0018): the same segments, speech text and cache files as the
 * [SegmentFeeder], so listening later finds them ready. It stops after [MAX_AHEAD] of audio (well within the
 * 300 MB cache), at the end of the book, or when the voice fails.
 */
class Prerenderer
    @Inject
    constructor(
        private val content: PlaybackContent,
        private val renderer: SegmentRenderer,
        private val planner: SegmentPlanner,
        private val speech: SpeechNormalizer,
        @DefaultDispatcher private val cpu: CoroutineDispatcher,
    ) {
        /**
         * Renders ahead in book [bookId], up to [ahead] of audio.
         *
         * @throws IOException when a chapter's text cannot be read.
         */
        suspend fun prerender(
            bookId: String,
            ahead: Duration = MAX_AHEAD,
        ): PrerenderOutcome {
            val book = (content.load(bookId) as? BookLoad.Loaded)?.book
            val voice = book?.voice?.takeIf { it.slowToRender } ?: return PrerenderOutcome.NotNeeded
            val plans = ChapterPlans(content, planner, cpu).apply { reset(book) }
            var cursor = plans.locate(book.position)
            var millis = 0L
            var failed = false
            while (cursor != null && millis < ahead.inWholeMilliseconds && !failed) {
                currentCoroutineContext().ensureActive()
                val audio = cursor.request(book.id, voice, speech)?.let { renderer.render(it) }
                if (audio is SegmentAudio.Ready) millis += audio.durationMillis
                failed = audio is SegmentAudio.Failed
                cursor = plans.after(cursor)
            }
            return if (failed) PrerenderOutcome.Failed else PrerenderOutcome.Ready(millis)
        }

        companion object {
            /** About 170 MB of Kokoro audio (24 kHz), comfortably below the cache's 300 MB. */
            val MAX_AHEAD: Duration = 60.minutes
        }
    }

/** The job of [WorkManagerPrerenderJobs]: renders ahead in one book while the phone charges ([Prerenderer]). */
@HiltWorker
class PrerenderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val prerenderer: Prerenderer,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val bookId = inputData.getString(BOOK_ID) ?: return Result.failure()
            return try {
                prerenderer.prerender(bookId)
                Result.success()
            } catch (e: IOException) {
                Diagnostics.recordHandled("render ahead", e)
                Result.failure()
            }
        }

        companion object {
            const val BOOK_ID = "book_id"
        }
    }
