package com.giovannimartins.rreader.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.textkit.text.SpeechNormalizer
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.InMemorySpeakingRateStore
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val texts = listOf(chapterText(0, 4), chapterText(1, 4))
    private val segments = SegmentPlanner().plan(texts[0])
    private val slow = voice(id = "neural:dora").copy(slowToRender = true)
    private val content =
        FakePlaybackContent(book(texts, voice = slow, position = ReadingPosition(0, segments[2].start)), texts)
    private val engine = FakeSpeechEngine()
    private val cache by lazy { AudioCache(folder.root) }
    private val prerenderer by lazy {
        val rates = SpeakingRateTracker(InMemorySpeakingRateStore(), CoroutineScope(Dispatchers.Unconfined))
        val renderer = SegmentRenderer(engine, cache, rates, Clock.systemUTC(), Dispatchers.Unconfined)
        Prerenderer(content, renderer, SegmentPlanner(), SpeechNormalizer(), Dispatchers.Unconfined)
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
            assertThat(cache.segmentFile(BOOK_ID, slow.id, chapter, range.start, range.end).isFile).isTrue()
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

        jobs.schedule("book-1")
        jobs.schedule(BOOK_ID)

        val job =
            workManager
                .getWorkInfosForUniqueWork(WorkManagerPrerenderJobs.UNIQUE_NAME)
                .get()
                .single { it.state == WorkInfo.State.ENQUEUED }
        assertThat(job.constraints.requiresCharging()).isTrue()
        assertThat(job.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(job.tags.none { BOOK_ID in it }).isTrue()
    }
}
