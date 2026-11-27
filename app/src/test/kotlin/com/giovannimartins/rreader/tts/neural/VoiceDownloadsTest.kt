package com.giovannimartins.rreader.tts.neural

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.giovannimartins.rreader.net.FakeTransport
import com.giovannimartins.rreader.net.FakeTransport.Companion.breakingBody
import com.giovannimartins.rreader.net.FakeTransport.Companion.ok
import com.giovannimartins.rreader.net.FakeTransport.Companion.redirect
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor

/** The voice download jobs: their network rules, what they report, when they retry and how they are cancelled. */
@RunWith(RobolectricTestRunner::class)
class VoiceDownloadsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val transport = FakeTransport()
    private val published: MutableList<Data> = Collections.synchronizedList(mutableListOf())
    private val tasks = HeldTasks()
    private val pkg = NeuralVoices.CADU
    private lateinit var workManager: WorkManager
    private lateinit var store: VoicePackageStore
    private lateinit var downloader: VoicePackageDownloader

    @Before
    fun setUp() {
        store = VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined)
        downloader =
            VoicePackageDownloader(VoiceModelHttp(transport, Dispatchers.Unconfined), store, Dispatchers.Unconfined)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(tasks)
                .setWorkerCoroutineContext(Dispatchers.Unconfined)
                .setWorkerFactory(workers)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun stopWork() {
        workManager.cancelAllWork().result.get()
    }

    @Test
    fun `waits for Wi-Fi unless the listener accepted mobile data, and a new choice replaces a waiting job`() {
        val downloads = WorkManagerVoiceDownloads(workManager, downloader)

        runBlocking { downloads.download(pkg, wifiOnly = true) }
        assertThat(job().constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(job().constraints.requiresStorageNotLow()).isTrue()
        assertThat(runBlocking { downloads.states.first() }).containsExactly(pkg.id, VoiceDownloadState.Waiting(true))

        runBlocking { downloads.download(pkg, wifiOnly = false) }

        assertThat(job().constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(runBlocking { downloads.states.first() }).containsExactly(pkg.id, VoiceDownloadState.Waiting(false))
    }

    @Test
    fun `cancelling stops the job and deletes what it received`() {
        val downloads = WorkManagerVoiceDownloads(workManager, downloader)
        runBlocking { downloads.download(pkg, wifiOnly = true) }
        store.partialFile(pkg).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(100))

        runBlocking { downloads.cancel(pkg) }

        assertThat(job().state).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(runBlocking { downloads.states.first() }).isEmpty()
    }

    @Test
    fun `cancelling waits for WorkManager, so an attempt that starts meanwhile leaves nothing behind`() {
        val downloads = WorkManagerVoiceDownloads(workManager, downloader)
        runBlocking { downloads.download(pkg, wifiOnly = true) }
        transport.answer(pkg.url) { redirect(ASSET) }
        transport.answer(ASSET) { ok(breakingBody(1_000), TYPE, contentLength = pkg.archiveBytes) }

        runBlocking {
            tasks.hold()
            val cancelling = launch { downloads.cancel(pkg) }
            yield()
            // An attempt of the job starts before WorkManager has cancelled it, and keeps what it received.
            assertThat(downloader.download(pkg))
                .isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.NETWORK, retryable = true))
            assertThat(store.partialFile(pkg).exists()).isTrue()
            tasks.release()
            cancelling.join()
        }

        assertThat(job().state).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(store.partialFile(pkg).exists()).isFalse()
    }

    @Test
    fun `removing a download that failed forgets its failure and deletes what it received`() {
        val downloads = WorkManagerVoiceDownloads(workManager, downloader)
        transport.answer(pkg.url) { FakeTransport.status(404) }
        runBlocking { downloads.download(pkg, wifiOnly = false) }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(job().id)
        val failed = VoiceDownloadState.Failed(VoiceDownloadFailure.UNAVAILABLE)
        assertThat(runBlocking { downloads.states.first { it.isNotEmpty() } }).containsExactly(pkg.id, failed)
        store.partialFile(pkg).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(100))
        store.stagingDirectory(pkg).mkdirs()

        runBlocking { downloads.cancel(pkg) }

        assertThat(runBlocking { downloads.states.first() }).isEmpty()
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.stagingDirectory(pkg).exists()).isFalse()
        runBlocking { downloads.download(pkg, wifiOnly = true) }
        assertThat(runBlocking { downloads.states.first() }).containsExactly(pkg.id, VoiceDownloadState.Waiting(true))
    }

    @Test
    fun `when the app starts, only what the waiting or running downloads received is kept`() {
        val downloads = WorkManagerVoiceDownloads(workManager, downloader)
        runBlocking { downloads.download(pkg, wifiOnly = true) }
        val failed = NeuralVoices.KOKORO
        transport.answer(failed.url) { FakeTransport.status(404) }
        runBlocking { downloads.download(failed, wifiOnly = false) }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(job(failed).id)
        val idle = NeuralVoices.FABER
        val downloadsFolder = store.partialFile(pkg).parentFile!!
        val stagingFolder = store.stagingDirectory(pkg).parentFile!!
        val kept = listOf(store.partialFile(pkg), File(store.stagingDirectory(pkg), "model.onnx"))
        val leftovers =
            listOf(
                store.partialFile(failed),
                store.partialFile(idle),
                File(store.stagingDirectory(idle), "model.onnx"),
                File(downloadsFolder, "${pkg.id}.part"),
                File(downloadsFolder, "${pkg.id}-0123456789abcdef.part"),
                File(downloadsFolder, "unknown.tmp"),
                File(stagingFolder, "retired/model.onnx"),
            )
        (kept + leftovers).forEach { it.apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10)) }

        runBlocking { downloads.discardLeftovers() }

        assertThat(job(failed).state).isEqualTo(WorkInfo.State.FAILED)
        assertThat(kept.filterNot(File::exists)).isEmpty()
        assertThat(leftovers.filter(File::exists)).isEmpty()
        assertThat(stagingFolder.list()?.toList()).containsExactly(pkg.id)
    }

    @Test
    fun `reads where each download stands from its job`() {
        assertThat(state(WorkInfo.State.ENQUEUED)).isEqualTo(VoiceDownloadState.Waiting(false))
        assertThat(state(WorkInfo.State.ENQUEUED, wifiOnly = true)).isEqualTo(VoiceDownloadState.Waiting(true))
        assertThat(state(WorkInfo.State.RUNNING)).isEqualTo(VoiceDownloadState.Downloading(0f))
        val halfway = VoiceDownloadWork.progress(VoiceDownloadProgress.Downloading(0.45f))
        assertThat(state(WorkInfo.State.RUNNING, progress = halfway)).isEqualTo(VoiceDownloadState.Downloading(0.45f))
        val installing = VoiceDownloadWork.progress(VoiceDownloadProgress.Installing)
        assertThat(state(WorkInfo.State.RUNNING, progress = installing)).isEqualTo(VoiceDownloadState.Installing)
        val damaged = VoiceDownloadWork.failed(VoiceDownloadFailure.DAMAGED)
        assertThat(
            state(WorkInfo.State.FAILED, output = damaged),
        ).isEqualTo(VoiceDownloadState.Failed(VoiceDownloadFailure.DAMAGED))
        assertThat(state(WorkInfo.State.FAILED)).isEqualTo(VoiceDownloadState.Failed(VoiceDownloadFailure.UNAVAILABLE))
        assertThat(state(WorkInfo.State.SUCCEEDED)).isNull()
        assertThat(state(WorkInfo.State.CANCELLED)).isNull()
        assertThat(
            VoiceDownloadWork.stateOf(
                WorkInfo(UUID.randomUUID(), WorkInfo.State.ENQUEUED, setOf(VoiceDownloadWork.TAG)),
            ),
        ).isNull()
    }

    @Test
    fun `the job gives up after six attempts in a row that received nothing, however many runs came before`() {
        transport.answer(pkg.url) { redirect(ASSET) }

        // Runs the system stopped (the Wi-Fi was lost, the time limit) counted as attempts too.
        val results = (1..VoicePackageDownloader.MAX_STALLED_ATTEMPTS).map { run(attempt = 20 + it) }

        assertThat(results.dropLast(1).toSet()).containsExactly(ListenableWorker.Result.retry())
        assertThat(state(WorkInfo.State.FAILED, output = results.last().outputData))
            .isEqualTo(VoiceDownloadState.Failed(VoiceDownloadFailure.NETWORK))
    }

    @Test
    fun `an attempt that receives part of the archive starts the count of attempts again`() {
        transport.answer(pkg.url) { redirect(ASSET) }
        val stalls = VoicePackageDownloader.MAX_STALLED_ATTEMPTS - 1
        val retry = ListenableWorker.Result.retry()
        assertThat((1..stalls).map { run(attempt = it) }.toSet()).containsExactly(retry)

        transport.answer(ASSET) { ok(breakingBody(1_000), TYPE, contentLength = pkg.archiveBytes) }
        assertThat(run(attempt = stalls + 1)).isEqualTo(retry)
        transport.answer(ASSET) { throw SocketTimeoutException("No answer") }

        assertThat((1..stalls).map { run(attempt = stalls + 1 + it) }.toSet()).containsExactly(retry)
        assertThat(store.partialFile(pkg).length()).isEqualTo(1_000)
        assertThat(run(attempt = 2 * stalls + 2)).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun `a download asked for again forgets the attempts of the one that gave up`() {
        transport.answer(pkg.url) { redirect(ASSET) }
        (1..VoicePackageDownloader.MAX_STALLED_ATTEMPTS).forEach { run(attempt = it) }

        assertThat(run(attempt = 0)).isEqualTo(ListenableWorker.Result.retry())
        assertThat(store.stalledAttempts(pkg)).isEqualTo(1)
    }

    @Test
    fun `waits longer by the same step before each new attempt`() {
        val request = VoiceDownloadRequests.download(pkg, wifiOnly = true)

        assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.LINEAR)
        assertThat(request.workSpec.backoffDelayDuration).isEqualTo(Duration.ofSeconds(30).toMillis())
    }

    @Test
    fun `the job does not retry a package that is not the published one`() {
        transport.answer(pkg.url) { redirect(ASSET) }
        transport.answer(ASSET) { ok(ByteArray(64), "application/octet-stream", contentLength = pkg.archiveBytes + 1) }

        val result = run(attempt = 0)

        assertThat(state(WorkInfo.State.FAILED, output = result.outputData))
            .isEqualTo(VoiceDownloadState.Failed(VoiceDownloadFailure.UNAVAILABLE))
        assertThat(published).isEmpty()
    }

    @Test
    fun `a job without a known package fails at once`() {
        val result = run(input = Data.EMPTY)

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(transport.requests).isEmpty()
    }

    private fun job(of: VoicePackage = pkg): WorkInfo =
        workManager.getWorkInfosForUniqueWork(VoiceDownloadWork.uniqueName(of)).get().single()

    private fun state(
        state: WorkInfo.State,
        wifiOnly: Boolean = false,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): VoiceDownloadState? {
        val tags =
            setOfNotNull(
                VoiceDownloadWork.TAG,
                VoiceDownloadWork.packageTag(
                    pkg,
                ),
                VoiceDownloadWork.WIFI_ONLY_TAG.takeIf {
                    wifiOnly
                },
            )
        val job = WorkInfo(UUID.randomUUID(), state, tags, outputData = output, progress = progress)
        return VoiceDownloadWork.stateOf(job)?.also { assertThat(it.first).isEqualTo(pkg.id) }?.second
    }

    private fun run(
        attempt: Int = 0,
        input: Data = VoiceDownloadWork.input(pkg),
    ): ListenableWorker.Result {
        val worker =
            TestListenableWorkerBuilder<VoiceDownloadWorker>(context)
                .setInputData(input)
                .setRunAttemptCount(attempt)
                .setWorkerFactory(workers)
                .setProgressUpdater { _, _, data ->
                    published += data
                    Futures.immediateVoidFuture()
                }.build()
        return runBlocking { worker.doWork() }
    }

    /** Download jobs over this test's downloader. */
    private val workers =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = VoiceDownloadWorker(appContext, workerParameters, downloader)
        }

    /** WorkManager's background tasks: run at once, or held from [hold] until [release], as a busy phone would. */
    private class HeldTasks : Executor {
        private val held = ArrayDeque<Runnable>()
        private var holding = false

        @Synchronized
        fun hold() {
            holding = true
        }

        fun release() {
            val waiting =
                synchronized(this) {
                    holding = false
                    held.toList().also { held.clear() }
                }
            waiting.forEach(Runnable::run)
        }

        override fun execute(command: Runnable) {
            val now =
                synchronized(this) {
                    if (holding) held.addLast(command)
                    !holding
                }
            if (now) command.run()
        }
    }

    private companion object {
        const val TYPE = "application/octet-stream"
        const val ASSET = "https://release-assets.githubusercontent.com/github-production-release-asset/1/cadu?sig=x"
    }
}
