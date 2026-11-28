package com.giovannimartins.rreader.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.InMemorySpeakingRateStore
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.testRenderSpeeds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** Rendering a slow voice ahead while the phone charges: what is rendered, where, and when the job runs. */
@RunWith(RobolectricTestRunner::class)
class PrerenderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The job for [BOOK_ID] on its [runAttempts]th run (0 is the first). */
    private fun worker(runAttempts: Int): PrerenderWorker =
        TestListenableWorkerBuilder<PrerenderWorker>(context)
            .setInputData(workDataOf(PrerenderWorker.BOOK_ID to BOOK_ID))
            .setRunAttemptCount(runAttempts)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = PrerenderWorker(appContext, workerParameters, prerenderer)
                },
            ).build()

    private val texts = listOf(chapterText(0, 4), chapterText(1, 4))
    private val segments = SegmentPlanner().plan(texts[0])
    private val slow = voice(id = "neural:dora").copy(slowToRender = true)
    private val content =
        FakePlaybackContent(book(texts, voice = slow, position = ReadingPosition(0, segments[2].start)), texts)
    private val engine = FakeSpeechEngine()
    private val cache by lazy { AudioCache(folder.root) }
    private val status = ListeningStatus()
    private val prerenderer by lazy {
        val rates = SpeakingRateTracker(InMemorySpeakingRateStore(), CoroutineScope(Dispatchers.Unconfined))
        val renderer =
            SegmentRenderer(engine, cache, rates, testRenderSpeeds(), Clock.systemUTC(), Dispatchers.Unconfined)
        Prerenderer(content, renderer, SegmentPlanner(), SpeechNormalizer(), status, Dispatchers.Unconfined)
    }

    @After
    fun stopWork() {
        if (WorkManagerTestInitHelper.getTestDriver(context) !=
            null
        ) {
            WorkManager
                .getInstance(context)
                .cancelAllWork()
                .result
                .get()
        }
    }

    @Test
    fun `renders the segments after the saved position into the cache files listening uses, into the next chapters`() {
        val outcome = runBlocking { prerenderer.prerender(BOOK_ID) }

        val expected =
            texts
                .flatMapIndexed { chapter, text ->
                    SegmentPlanner()
                        .plan(
                            text,
                        ).map { chapter to it }
                }.drop(2)
        assertThat(engine.spoken).hasSize(expected.size)
        expected.forEach { (chapter, range) ->
            assertThat(cache.segmentFile(BOOK_ID, slow.audioKey, chapter, range.start, range.end).isFile).isTrue()
        }
        assertThat((outcome as PrerenderOutcome.Ready).millis).isGreaterThan(0)
        // Rendered again, everything comes from the cache.
        runBlocking { prerenderer.prerender(BOOK_ID) }
        assertThat(engine.spoken).hasSize(expected.size)
    }

    @Test
    fun `stops after the audio asked for`() {
        val outcome = runBlocking { prerenderer.prerender(BOOK_ID, ahead = 1.milliseconds) }

        assertThat(engine.spoken).hasSize(1)
        assertThat(outcome).isInstanceOf(PrerenderOutcome.Ready::class.java)
    }

    @Test
    fun `a voice that keeps up, or no voice, needs nothing rendered ahead`() {
        content.book = content.book?.copy(voice = voice(id = "neural:cadu"))
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isEqualTo(PrerenderOutcome.NotNeeded)
        content.book = content.book?.copy(voice = null)
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isEqualTo(PrerenderOutcome.NotNeeded)

        assertThat(engine.spoken).isEmpty()
    }

    @Test
    fun `stops at the first failure of the voice`() {
        engine.failure = SynthesisFailure.RENDER_ERROR

        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isEqualTo(PrerenderOutcome.Failed)
        assertThat(engine.spoken).hasSize(1)
    }

    @Test
    fun `listening holds it back, and starting to listen stops it after the segment being rendered`() {
        status.publishSession(ListeningSession(BOOK_ID, slow, listening = true))
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isEqualTo(PrerenderOutcome.Yielded)
        assertThat(engine.spoken).isEmpty()
        status.publishSession(ListeningSession(BOOK_ID, slow, listening = false))
        engine.gate = CompletableDeferred()
        engine.rendersBeforeGate = 2

        val outcome =
            runBlocking {
                val rendering = async(Dispatchers.Default) { prerenderer.prerender(BOOK_ID) }
                engine.atGate.await()
                status.publishSession(ListeningSession(BOOK_ID, slow, listening = true))
                rendering.await()
            }

        assertThat(outcome).isEqualTo(PrerenderOutcome.Yielded)
        assertThat(engine.spoken).hasSize(2)
        assertThat(
            folder.root
                .walkTopDown()
                .filter { it.name.endsWith(".part") }
                .toList(),
        ).isEmpty()
    }

    @Test
    fun `a book open with a voice of another model holds it back, so the listener's model stays loaded`() {
        status.publishSession(ListeningSession(OTHER_BOOK, voice(id = "neural:cadu"), listening = false))
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isEqualTo(PrerenderOutcome.Yielded)
        assertThat(engine.spoken).isEmpty()

        status.publishSession(ListeningSession(OTHER_BOOK, voice(id = "neural:alex"), listening = false))
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isInstanceOf(PrerenderOutcome.Ready::class.java)
        status.publishSession(ListeningSession(OTHER_BOOK, voice(id = "system:engine:pt-br"), listening = false))
        assertThat(runBlocking { prerenderer.prerender(BOOK_ID) }).isInstanceOf(PrerenderOutcome.Ready::class.java)
        assertThat(engine.spoken).isNotEmpty()
    }

    @Test
    fun `a job started three times without ending is given up, so a crash in it cannot repeat for good`() {
        val givenUp = runBlocking { worker(runAttempts = PrerenderWorker.MAX_RUN_ATTEMPTS).doWork() }

        assertThat(givenUp).isEqualTo(ListenableWorker.Result.failure())
        assertThat(engine.spoken).isEmpty()
        val lastTry = runBlocking { worker(runAttempts = PrerenderWorker.MAX_RUN_ATTEMPTS - 1).doWork() }
        assertThat(lastTry).isEqualTo(ListenableWorker.Result.success())
        assertThat(engine.spoken).isNotEmpty()
    }

    @Test
    fun `the job runs only while the phone charges, for the latest book`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
        val workManager = WorkManager.getInstance(context)
        val jobs = WorkManagerPrerenderJobs(workManager)

        jobs.schedule(OTHER_BOOK, slow.id)
        jobs.schedule(BOOK_ID, slow.id)

        val job =
            workManager
                .getWorkInfosForUniqueWork(WorkManagerPrerenderJobs.UNIQUE_NAME)
                .get()
                .single { it.state == WorkInfo.State.ENQUEUED }
        assertThat(job.constraints.requiresCharging()).isTrue()
        assertThat(job.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(job.tags.none { BOOK_ID in it }).isTrue()
    }

    @Test
    fun `asking again for the same book and voice keeps the job, another book or voice replaces it`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
        val workManager = WorkManager.getInstance(context)
        val jobs = WorkManagerPrerenderJobs(workManager)
        val pending = {
            workManager
                .getWorkInfosForUniqueWork(WorkManagerPrerenderJobs.UNIQUE_NAME)
                .get()
                .single { !it.state.isFinished }
                .id
        }

        jobs.schedule(BOOK_ID, "neural:dora")
        val first = pending()
        jobs.schedule(BOOK_ID, "neural:dora")
        assertThat(pending()).isEqualTo(first)
        jobs.schedule(BOOK_ID, "neural:alex")
        val second = pending()
        jobs.schedule(OTHER_BOOK, "neural:alex")

        assertThat(second).isNotEqualTo(first)
        assertThat(pending()).isNotEqualTo(second)
    }

    private companion object {
        const val OTHER_BOOK = "00000000-0000-0000-0000-000000000002"
    }
}
