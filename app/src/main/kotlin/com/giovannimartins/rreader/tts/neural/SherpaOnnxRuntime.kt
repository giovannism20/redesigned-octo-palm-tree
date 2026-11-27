package com.giovannimartins.rreader.tts.neural

import android.content.Context
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NeuralRuntime] on the native libraries fetched by the build (ADR 0018, app/libs/README.md): piper-phonemize
 * 1.6.1 with the Portuguese espeak-ng data the app ships, and sherpa-onnx 2.0.3, which takes the phonemes as code
 * points (it no longer phonemizes text itself). Models run on the CPU with up to [MAX_THREADS] threads.
 */
@Singleton
class SherpaOnnxRuntime
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NeuralRuntime {
        /** Whether espeak-ng started; null before the first try. It can only be started once per process. */
        private var phonemizerReady: Boolean? = null

        @Synchronized
        override fun phonemize(text: String): List<IntArray> {
            startPhonemizer()
            val result = piperPhonemizeText(text, ESPEAK_VOICE) ?: throw NeuralRuntimeException("No phonemes")
            try {
                return (0 until result.numSentences).map(result::getPhonemes)
            } finally {
                result.close()
            }
        }

        override fun load(
            pkg: VoicePackage,
            directory: File,
        ): NeuralModel {
            val model = File(directory, "model.onnx").path
            val tokens = File(directory, "tokens.txt").path
            val models =
                when (pkg.family) {
                    ModelFamily.KOKORO -> {
                        val voices = File(directory, "voices.bin").path
                        // Kokoro's default language is English: Portuguese must be named.
                        OfflineTtsModelConfig(
                            kokoro =
                                OfflineTtsKokoroModelConfig(
                                    model = model,
                                    voices = voices,
                                    tokens = tokens,
                                    lang = LANGUAGE,
                                ),
                            numThreads = threads(),
                            provider = CPU,
                        )
                    }

                    ModelFamily.PIPER -> {
                        OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(model = model, tokens = tokens),
                            numThreads = threads(),
                            provider = CPU,
                        )
                    }
                }
            val tts =
                try {
                    OfflineTts(null, OfflineTtsConfig(model = models, maxNumSentences = 1, silenceScale = 1f))
                } catch (e: IllegalArgumentException) {
                    throw NeuralRuntimeException("The voice model did not load", e)
                } catch (e: LinkageError) {
                    throw NeuralRuntimeException("The speech library is missing", e)
                }
            return SherpaOnnxModel(tts)
        }

        /**
         * Starts espeak-ng once. When it fails to start, it is not tried again in the same process; when only its
         * data could not be copied (storage full), the next voice that needs it tries again.
         */
        private fun startPhonemizer() {
            val ready = phonemizerReady ?: initializePhonemizer(espeakData()).also { phonemizerReady = it }
            if (!ready) throw NeuralRuntimeException("The phonemizer could not start")
        }

        private fun espeakData(): File =
            try {
                EspeakData.install(context)
            } catch (e: IOException) {
                throw NeuralRuntimeException("The phonemizer's data could not be copied", e)
            }

        /** Whether espeak-ng started: it reports its sample rate when it starts, and -1 when it cannot. */
        private fun initializePhonemizer(data: File): Boolean =
            try {
                piperPhonemizeInitialize(data.path) > 0
            } catch (e: LinkageError) {
                throw NeuralRuntimeException("The phonemizer library is missing", e)
            }

        private fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)

        private companion object {
            /** espeak-ng's Brazilian Portuguese, for every voice (Kokoro's own default is English). */
            const val ESPEAK_VOICE = "pt-br"
            const val LANGUAGE = "pt-br"
            const val CPU = "cpu"
            const val MAX_THREADS = 4
        }
    }

/** A sherpa-onnx model; its pauses are left as the model makes them (silence scale 1). */
private class SherpaOnnxModel(
    private val tts: OfflineTts,
) : NeuralModel {
    override val sampleRate: Int = tts.sampleRate()

    override fun generate(
        sentences: List<IntArray>,
        speakerId: Int,
        keepGoing: () -> Boolean,
    ): FloatArray {
        val phonemes = sentences.toTypedArray()
        val config = GenerationConfig(silenceScale = 1f, speed = 1f, sid = speakerId, phonemeCodepoints = phonemes)
        // The text is empty: sherpa-onnx speaks the phonemes given in the configuration.
        val audio = tts.generateWithConfigAndCallback("", config) { if (keepGoing()) CONTINUE else STOP }
        return audio.samples
    }

    override fun close() = tts.release()

    private companion object {
        const val CONTINUE = 1
        const val STOP = 0
    }
}

/**
 * The Portuguese espeak-ng data shipped in `assets/espeak-pt` (unmodified files of piper-phonemize 1.6.1), copied
 * once to private storage, because espeak-ng reads files from a folder. A new data version gets a new folder.
 */
internal object EspeakData {
    private const val ASSETS = "espeak-pt"
    private const val VERSION = "piper-phonemize-1.6.1"
    private const val COMPLETE = ".complete"
    val FILES = listOf("phontab", "phonindex", "phondata", "intonations", "pt_dict", "lang/roa/pt", "lang/roa/pt-BR")

    /** The folder holding the data, copying it first when needed. */
    fun install(context: Context): File {
        val root = File(context.noBackupFilesDir, "espeak-ng-data")
        val folder = File(root, VERSION)
        if (File(folder, COMPLETE).isFile) return folder
        root.deleteRecursively()
        FILES.forEach { name ->
            val target = File(folder, name).apply { parentFile?.mkdirs() }
            context.assets.open("$ASSETS/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        File(folder, COMPLETE).createNewFile()
        return folder
    }
}
