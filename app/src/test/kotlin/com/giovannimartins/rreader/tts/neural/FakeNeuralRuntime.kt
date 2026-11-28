package com.giovannimartins.rreader.tts.neural

import java.io.File

/** A native layer that never touches native code: one phoneme per character, [samplesPerPhoneme] samples each. */
internal class FakeNeuralRuntime(
    private val sampleRate: Int = 10_000,
    private val samplesPerPhoneme: Int = 100,
) : NeuralRuntime {
    var starts = 0
    val phonemized = mutableListOf<String>()
    val loads = mutableListOf<VoicePackage>()
    val generated = mutableListOf<Pair<List<String>, Int>>()
    val closed = mutableListOf<VoicePackage>()
    var failLoading = false
    var failGenerating = false

    /** Called as the phonemizer starts, as text is phonemized and as a model loads; tests use them to fail or die. */
    var onStart: () -> Unit = {}
    var onPhonemize: (String) -> Unit = {}
    var onLoad: (VoicePackage) -> Unit = {}

    /** Called while generating, with the stop signal; tests use it to cancel or wait. */
    var onGenerate: (keepGoing: () -> Boolean) -> Unit = {}

    override fun startPhonemizer() {
        onStart()
        starts++
    }

    override fun phonemize(text: String): List<IntArray> {
        onPhonemize(text)
        phonemized += text
        return text
            .split('.')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.codePoints().toArray() }
    }

    override fun load(
        pkg: VoicePackage,
        directory: File,
    ): NeuralModel {
        if (failLoading) throw NeuralRuntimeException("No model")
        onLoad(pkg)
        loads += pkg
        return object : NeuralModel {
            override val sampleRate: Int = this@FakeNeuralRuntime.sampleRate

            override fun generate(
                sentences: List<IntArray>,
                speakerId: Int,
                speed: Float,
                keepGoing: () -> Boolean,
            ): FloatArray {
                if (failGenerating) throw IllegalStateException("Model failed")
                generated += sentences.map { String(it, 0, it.size) } to speakerId
                onGenerate(keepGoing)
                // A tone at half the sample rate: no DC offset, nothing for the high-pass to remove.
                return FloatArray(sentences.sumOf { it.size } * samplesPerPhoneme) { if (it % 2 == 0) 0.5f else -0.5f }
            }

            override fun close() {
                closed += pkg
            }
        }
    }
}
