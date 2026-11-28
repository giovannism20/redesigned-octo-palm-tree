package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.nativecall.NativeCallNote
import com.giovannimartins.rreader.textkit.model.TextRange
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.InMemorySpeakingRateStore
import com.giovannimartins.rreader.tts.SegmentAudio
import com.giovannimartins.rreader.tts.SegmentRenderer
import com.giovannimartins.rreader.tts.SegmentRequest
import com.giovannimartins.rreader.tts.SpeakingRateTracker
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.testRenderSpeeds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock

/**
 * SR-4 for the neural voices: a crash in espeak-ng or ONNX Runtime kills the process, so the fake native layer "kills
 * the process" with an Error nothing catches, and the test puts back the note the call left behind, as a real crash
 * would. Each [process] is a new engine reading that note. Under Robolectric for the Android log.
 */
@RunWith(RobolectricTestRunner::class)
class NeuralCallMarkersTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val runtime = FakeNeuralRuntime()
    private val store by lazy { VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined) }
    private val marker by lazy { File(folder.root, "neural.inflight") }
    private val cadu = NeuralVoices.all.first { it.key == "cadu" }
    private val output get() = File(folder.root, "out/segment.wav")

    @Test
    fun `a segment whose speech killed the app twice in a row becomes a pause, and the next one is spoken`() {
        install(NeuralVoices.CADU)
        val note = dieWhile(Stage.SPEAKING)
        dieWhile(Stage.SPEAKING)
        val engine = process()
        runtime.generated.clear()

        val skipped = synthesize(engine, TEXT)

        assertThat(note).matches("speak cadu [0-9a-f]{16} 0")
        assertThat(skipped).isEqualTo(SynthesisResult.Rendered(NeuralAudio.SEGMENT_PAUSE_MILLIS.toLong()))
        assertThat(runtime.generated).isEmpty()
        assertThat(synthesize(engine, "Outra frase.")).isInstanceOf(SynthesisResult.Rendered::class.java)
        assertThat(runtime.generated.map { it.first }).containsExactly(listOf("Outra frase"))
    }

    @Test
    fun `a process killed once in a segment loses nothing`() {
        install(NeuralVoices.CADU)
        dieWhile(Stage.SPEAKING)
        runtime.generated.clear()

        val result = synthesize(process(), TEXT)

        assertThat((result as SynthesisResult.Rendered).durationMillis).isGreaterThan(250L)
        assertThat(runtime.generated).hasSize(1)
        assertThat(marker.exists()).isFalse()
    }

    @Test
    fun `a model whose loading killed the app twice in a row is not loaded again`() {
        install(NeuralVoices.CADU)
        dieWhile(Stage.LOADING)
        dieWhile(Stage.LOADING)
        val loads = runtime.loads.size

        repeat(2) {
            assertThat(synthesize(process(), TEXT))
                .isEqualTo(SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE))
        }

        assertThat(runtime.loads).hasSize(loads)
        assertThat(runtime.generated).isEmpty()
    }

    @Test
    fun `deleting a package forgets that its model killed the app, so the package is tried again once back`() {
        install(NeuralVoices.CADU)
        dieWhile(Stage.LOADING)
        dieWhile(Stage.LOADING)
        val engine = process()
        assertThat(synthesize(engine, TEXT)).isEqualTo(SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE))

        runBlocking { engine.deleting(NeuralVoices.CADU) { store.delete(NeuralVoices.CADU) } }
        install(NeuralVoices.CADU)

        assertThat(synthesize(engine, TEXT)).isInstanceOf(SynthesisResult.Rendered::class.java)
        assertThat(synthesize(process(), "Outra frase.")).isInstanceOf(SynthesisResult.Rendered::class.java)
    }

    @Test
    fun `espeak-ng that killed the app twice in a row as it started is not started again`() {
        install(NeuralVoices.CADU)
        dieWhile(Stage.STARTING)
        dieWhile(Stage.STARTING)

        val result = synthesize(process(), TEXT)

        assertThat(result).isEqualTo(SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE))
        assertThat(runtime.starts).isEqualTo(0)
        assertThat(runtime.loads).isEmpty()
    }

    @Test
    fun `listening does not loop on a segment that kills the app, since its pause is kept in the audio cache`() {
        install(NeuralVoices.CADU)
        repeat(NativeCallNote.MAX_DEATHS) {
            armDeath(Stage.SPEAKING)
            assertThrows(ProcessDeath::class.java) { runBlocking { renderer(process()).render(request()) } }
            restoreNote()
        }

        val third = runBlocking { renderer(process()).render(request()) }
        runtime.onGenerate = { error("the segment must come from the cache") }
        val fourth = runBlocking { renderer(process()).render(request()) }

        assertThat(third).isInstanceOf(SegmentAudio.Ready::class.java)
        assertThat((fourth as SegmentAudio.Ready).file).isEqualTo((third as SegmentAudio.Ready).file)
        assertThat(fourth.durationMillis).isEqualTo(NeuralAudio.SEGMENT_PAUSE_MILLIS.toLong())
    }

    private enum class Stage { STARTING, LOADING, SPEAKING }

    private var left = ByteArray(0)

    /** A process that dies at [stage] of rendering [TEXT]; returns the note it left behind, now back on disk. */
    private fun dieWhile(stage: Stage): String {
        armDeath(stage)
        assertThrows(ProcessDeath::class.java) { synthesize(process(), TEXT) }
        restoreNote()
        return left.decodeToString()
    }

    /** Makes the next call at [stage] die, keeping the note it finds on disk. */
    private fun armDeath(stage: Stage) {
        val die = {
            left = marker.readBytes()
            disarm()
            throw ProcessDeath()
        }
        when (stage) {
            Stage.STARTING -> runtime.onStart = { die() }
            Stage.LOADING -> runtime.onLoad = { die() }
            Stage.SPEAKING -> runtime.onGenerate = { die() }
        }
    }

    private fun disarm() {
        runtime.onStart = {}
        runtime.onLoad = {}
        runtime.onGenerate = {}
    }

    /** The note a dying call found is what a real crash leaves on disk: the app's own clean-up never ran. */
    private fun restoreNote() {
        marker.writeBytes(left)
    }

    /** A new process: a new engine, whose markers read the note the process before it left behind. */
    private fun process() =
        SherpaOnnxEngine(store, runtime, NeuralCallMarkers(marker), Dispatchers.Default, TestScope())

    private fun synthesize(
        engine: SherpaOnnxEngine,
        text: String,
    ): SynthesisResult = runBlocking { engine.synthesize(text, cadu.toVoice(), output) }

    private fun renderer(engine: SherpaOnnxEngine): SegmentRenderer {
        val rates = SpeakingRateTracker(InMemorySpeakingRateStore(), CoroutineScope(Dispatchers.Unconfined))
        return SegmentRenderer(
            engine,
            AudioCache(File(folder.root, "cache")),
            rates,
            testRenderSpeeds(),
            Clock.systemUTC(),
            Dispatchers.IO,
        )
    }

    private fun request() = SegmentRequest(BOOK_ID, cadu.toVoice(), 0, TextRange(0, TEXT.length), TEXT)

    /** Puts files of the right sizes where [pkg] is installed (sparse: only their length matters). */
    private fun install(pkg: VoicePackage) {
        pkg.files.forEach { file ->
            java.io.RandomAccessFile(store.file(pkg, file.name).apply { parentFile?.mkdirs() }, "rw").use {
                it.setLength(file.bytes)
            }
        }
    }

    /** Stands for the process being killed by a crash in native code: nothing in the app catches it. */
    private class ProcessDeath : Error("process died in native code")

    private companion object {
        const val TEXT = "Olá, leitor."
        const val BOOK_ID = "00000000-0000-0000-0000-000000000001"
    }
}
