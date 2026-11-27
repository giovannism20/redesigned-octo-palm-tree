package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.playback.voice
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.WavInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The neural engine over a fake native layer: what it asks the models, writes and refuses. */
@RunWith(RobolectricTestRunner::class)
class SherpaOnnxEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val runtime = FakeNeuralRuntime()
    private val store by lazy { VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined) }
    private val engine by lazy {
        SherpaOnnxEngine(store, runtime, NeuralCallMarkers(File(folder.root, "neural.inflight")), Dispatchers.Default)
    }
    private val dora = NeuralVoices.all.first { it.key == "dora" }
    private val alex = NeuralVoices.all.first { it.key == "alex" }
    private val cadu = NeuralVoices.all.first { it.key == "cadu" }

    @Test
    fun `offers the voices of the installed packages only, most recommended first, as offline Portuguese voices`() {
        install(NeuralVoices.KOKORO)
        install(NeuralVoices.CADU)

        val voices = runBlocking { engine.voices() }

        assertThat(
            voices.map { it.id },
        ).containsExactly("neural:dora", "neural:alex", "neural:cadu", "neural:santa").inOrder()
        assertThat(voices.map { it.locale.toLanguageTag() }.toSet()).containsExactly("pt-BR")
        assertThat(voices.none { it.requiresNetwork }).isTrue()
        assertThat(voices.filter { it.slowToRender }.map { it.name }).containsExactly("Dora", "Alex", "Santa")
    }

    @Test
    fun `speaks Kokoro voices with their speaker and merged phonemes, after a pause for a dialogue dash`() {
        install(NeuralVoices.KOKORO)

        val result = synthesize(dora, "— Era tʃa. Noite.")

        assertThat(runtime.phonemized).containsExactly("Era tʃa. Noite.")
        assertThat(runtime.generated).containsExactly(listOf("Era ʧa", "Noite") to 42)
        val millis = (result as SynthesisResult.Rendered).durationMillis
        assertThat(WavInfo.durationMillis(output)).isEqualTo(millis)
        // 11 phonemes of 10 ms each, a 300 ms dash pause before them and a 250 ms pause after.
        assertThat(millis).isEqualTo(110L + 300 + 250)
    }

    @Test
    fun `speaks Piper voices with speaker 0 and their phonemes as they are`() {
        install(NeuralVoices.CADU)

        synthesize(cadu, "Era tʃa.")

        assertThat(runtime.generated).containsExactly(listOf("Era tʃa") to 0)
    }

    @Test
    fun `keeps one model loaded and replaces it when another package speaks`() {
        install(NeuralVoices.KOKORO)
        install(NeuralVoices.CADU)

        synthesize(dora, "Um.")
        synthesize(alex, "Dois.")
        synthesize(cadu, "Três.")

        assertThat(runtime.loads).containsExactly(NeuralVoices.KOKORO, NeuralVoices.CADU).inOrder()
        assertThat(runtime.closed).containsExactly(NeuralVoices.KOKORO)
        runBlocking { engine.unload(NeuralVoices.CADU) }
        assertThat(runtime.closed).containsExactly(NeuralVoices.KOKORO, NeuralVoices.CADU).inOrder()
    }

    @Test
    fun `a voice whose package is missing, or that is not neural, is unavailable`() {
        assertThat(synthesize(dora, "Olá.")).isEqualTo(SynthesisResult.Failed(SynthesisFailure.VOICE_UNAVAILABLE))
        val phoneVoice = voice(id = "system:com.example.tts:pt-br-x-1")
        assertThat(runBlocking { engine.synthesize("Olá.", phoneVoice, output) })
            .isEqualTo(SynthesisResult.Failed(SynthesisFailure.VOICE_UNAVAILABLE))
        assertThat(runtime.loads).isEmpty()
    }

    @Test
    fun `native failures are typed failures, never crashes`() {
        install(NeuralVoices.CADU)
        runtime.failLoading = true
        assertThat(synthesize(cadu, "Olá.")).isEqualTo(SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE))

        runtime.failLoading = false
        runtime.failGenerating = true
        assertThat(synthesize(cadu, "Olá.")).isEqualTo(SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR))
    }

    @Test
    fun `a missing library or method, or memory running out, makes the voice unavailable and frees its model`() {
        install(NeuralVoices.CADU)
        val unavailable = SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE)

        runtime.onGenerate = { raise(OutOfMemoryError("model")) }
        assertThat(synthesize(cadu, "Olá.")).isEqualTo(unavailable)
        assertThat(runtime.closed).containsExactly(NeuralVoices.CADU)

        runtime.onGenerate = {}
        runtime.onPhonemize = { raise(NoSuchMethodError("piperPhonemizeText")) }
        assertThat(synthesize(cadu, "Olá.")).isEqualTo(unavailable)
        assertThat(runtime.closed).containsExactly(NeuralVoices.CADU, NeuralVoices.CADU)

        runtime.onPhonemize = {}
        runtime.onLoad = { raise(UnsatisfiedLinkError("sherpa-onnx-jni")) }
        assertThat(synthesize(cadu, "Olá.")).isEqualTo(unavailable)

        runtime.onLoad = {}
        assertThat(synthesize(cadu, "Olá.")).isInstanceOf(SynthesisResult.Rendered::class.java)
    }

    @Test
    fun `text without phonemes becomes a short pause`() {
        install(NeuralVoices.CADU)

        val result = synthesize(cadu, "...")

        assertThat(result).isEqualTo(SynthesisResult.Rendered(NeuralAudio.SEGMENT_PAUSE_MILLIS.toLong()))
        assertThat(runtime.generated).isEmpty()
    }

    @Test
    fun `a cancelled render tells the model to stop`() {
        install(NeuralVoices.CADU)
        val generating = CompletableDeferred<() -> Boolean>()
        val release = CompletableDeferred<Unit>()
        runtime.onGenerate = { keepGoing ->
            generating.complete(keepGoing)
            runBlocking { release.await() }
        }

        val stillGoing =
            runBlocking {
                val render = async(Dispatchers.Default) { engine.synthesize("Olá.", cadu.toVoice(), output) }
                val keepGoing = generating.await()
                render.cancel()
                val answer = keepGoing()
                release.complete(Unit)
                answer
            }

        assertThat(stillGoing).isFalse()
        assertThat(output.exists()).isFalse()
    }

    private val output get() = File(folder.root, "out/segment.wav")

    /** What the native layer throws as a Java error. */
    private fun raise(error: Error): Nothing = throw error

    private fun synthesize(
        voice: NeuralVoice,
        text: String,
    ): SynthesisResult = runBlocking { engine.synthesize(text, voice.toVoice(), output) }

    /** Puts files of the right sizes where [pkg] is installed (sparse: only their length matters). */
    private fun install(pkg: VoicePackage) {
        pkg.files.forEach { file ->
            java.io.RandomAccessFile(store.file(pkg, file.name).apply { parentFile?.mkdirs() }, "rw").use {
                it.setLength(file.bytes)
            }
        }
    }
}
