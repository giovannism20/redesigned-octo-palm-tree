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
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Asks for a book to be rendered ahead of listening while the phone charges. */
fun interface PrerenderJobs {
    /**
     * Renders ahead in book [bookId], read by voice [voiceId], the next time the phone charges, instead of any book
     * asked for before. A job asked for the same book and voice is kept as it is, running or waiting.
     */
    fun schedule(
        bookId: String,
        voiceId: String,
    )
}

/**
 * [PrerenderJobs] on WorkManager: one job for the whole app ([UNIQUE_NAME], the latest book replaces the one before),
 * which runs only while the phone charges, with enough battery and storage, and stops when it is unplugged. Its name
 * and tags say nothing about the book (SR-8).
 *
 * The player asks again each time listening stops, so a job asked for the same book and voice as the last one is kept
 * ([ExistingWorkPolicy.KEEP]): a running job goes on, and a job the process died in keeps its count of runs, which
 * [PrerenderWorker] caps. Another book or voice replaces it. The last request is remembered in memory only, so the
 * first one after the app starts replaces whatever job waits.
 */
@Singleton
class WorkManagerPrerenderJobs
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : PrerenderJobs {
        /** The book and voice asked for last in this process. */
        private var last: Pair<String, String>? = null

        @Synchronized
        override fun schedule(
            bookId: String,
            voiceId: String,
        ) {
            val asked = bookId to voiceId
            val policy = if (asked == last) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            last = asked
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
            workManager.enqueueUniqueWork(UNIQUE_NAME, policy, request)
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

    /**
     * Stopped for the listener: a book is being listened to, or the open book's voice needs another model, which this
     * job would load in its place. The player asks again when listening stops.
     */
    data object Yielded : PrerenderOutcome
}

/**
 * Renders the segments after a book's saved reading position into the audio cache, for voices that may be slower
 * than real time on a phone (Kokoro, ADR 0018): the same segments, speech text and cache files as the
 * [SegmentFeeder], so listening later finds them ready. It stops after [MAX_AHEAD] of audio (well within the
 * 300 MB cache), at the end of the book, or when the voice fails.
 *
 * It never competes with listening: it does not start, and stops after the sentence being spoken, while a book is
 * listened to or the open book's voice needs another model (loading it would unload the listener's); and it renders
 * in the background, waiting while the player fills its render-ahead window ([SegmentRenderer.inForeground]).
 */
class Prerenderer
    @Inject
    constructor(
        private val content: PlaybackContent,
        private val renderer: SegmentRenderer,
        private val planner: SegmentPlanner,
        private val speech: SpeechNormalizer,
        private val status: ListeningStatus,
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
            return untilHeldBack(voice) { renderAhead(book, voice, ahead) } ?: PrerenderOutcome.Yielded
        }

        /**
         * Runs [render] unless, or until, the listening session holds back pre-rendering with [voice]: then null, and
         * the render in progress is cancelled (the model stops after the sentence it is speaking).
         */
        private suspend fun untilHeldBack(
            voice: Voice,
            render: suspend () -> PrerenderOutcome,
        ): PrerenderOutcome? {
            if (status.session.value.holdsBack(voice)) return null
            val heldBack: Flow<PrerenderOutcome?> = status.session.filter { it.holdsBack(voice) }.map { null }
            // Whichever comes first wins; the other is cancelled.
            return merge(flow { emit(render()) }, heldBack).first()
        }

        private suspend fun renderAhead(
            book: PlaybackBook,
            voice: Voice,
            ahead: Duration,
        ): PrerenderOutcome {
            val plans = ChapterPlans(content, planner, cpu).apply { reset(book) }
            var cursor = plans.locate(book.position)
            var millis = 0L
            var failed = false
            while (cursor != null && millis < ahead.inWholeMilliseconds && !failed) {
                currentCoroutineContext().ensureActive()
                val audio =
                    cursor
                        .request(
                            book.id,
                            voice,
                            speech,
                            book.speed,
                        )?.let { renderer.render(it, background = true) }
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

/**
 * Whether this session keeps pre-rendering with [voice] from running: a book is being listened to, or the open book is
 * read by a neural voice of another package, whose model the job would replace.
 */
internal fun ListeningSession?.holdsBack(voice: Voice): Boolean {
    val session = this ?: return false
    val listenersModel = NeuralVoices.byId(session.voice?.id)?.pkg
    return session.listening || (listenersModel != null && listenersModel != NeuralVoices.byId(voice.id)?.pkg)
}

/**
 * The job of [WorkManagerPrerenderJobs]: renders ahead in one book while the phone charges ([Prerenderer]). WorkManager
 * runs a job again when the process died in it, so a job started [MAX_RUN_ATTEMPTS] times without ending is given up:
 * whatever kills the process there, it cannot keep doing so for good.
 */
@HiltWorker
class PrerenderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val prerenderer: Prerenderer,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val bookId = inputData.getString(BOOK_ID)
            if (bookId == null || runAttemptCount >= MAX_RUN_ATTEMPTS) return Result.failure()
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

            /** Runs of one job, the first included, before it is given up. */
            const val MAX_RUN_ATTEMPTS: Int = 3
        }
    }
