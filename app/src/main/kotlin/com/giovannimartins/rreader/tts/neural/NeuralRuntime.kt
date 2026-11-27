package com.giovannimartins.rreader.tts.neural

import java.io.File

/** The native layer could not do what was asked: a library, the phonemizer's data or a model did not load. */
class NeuralRuntimeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The native half of the neural voices: espeak-ng phonemes through piper-phonemize, and sherpa-onnx models. Behind
 * this interface so [SherpaOnnxEngine] runs in JVM tests with a fake; the app uses [SherpaOnnxRuntime].
 */
interface NeuralRuntime {
    /**
     * The Brazilian Portuguese phonemes of [text], one array of Unicode code points per sentence.
     *
     * @throws NeuralRuntimeException when the phonemizer cannot start.
     */
    fun phonemize(text: String): List<IntArray>

    /**
     * Loads the model of [pkg] from its installed files in [directory].
     *
     * @throws NeuralRuntimeException when it cannot be loaded.
     */
    fun load(
        pkg: VoicePackage,
        directory: File,
    ): NeuralModel
}

/** A loaded voice model, used by one thread at a time. */
interface NeuralModel : AutoCloseable {
    val sampleRate: Int

    /**
     * Speaks [sentences] (phoneme code points, one array per sentence) as speaker [speakerId]: samples in [-1, 1] at
     * [sampleRate]. [keepGoing] is asked as each sentence is done; when it answers false, generation stops early.
     *
     * @throws NeuralRuntimeException when the model fails.
     */
    fun generate(
        sentences: List<IntArray>,
        speakerId: Int,
        keepGoing: () -> Boolean,
    ): FloatArray
}
