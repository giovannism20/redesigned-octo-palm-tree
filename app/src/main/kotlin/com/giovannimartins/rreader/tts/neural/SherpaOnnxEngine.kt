package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.tts.PcmWav
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpeechEngine] for the offline neural voices (ADR 0018), writing WAV files into the same rendering pipeline as the
 * phone's voices (ADR 0003). A segment's text is prepared ([NeuralText]: a leading dialogue dash becomes a pause),
 * phonemized in Brazilian Portuguese (Kokoro's phonemes merged as [KokoroPhonemes] says), spoken by the voice's
 * model with its speaker id, and finished by [NeuralAudio] (DC offset removed, edges trimmed, the voice's gain, a
 * pause after it). Renders run one at a time; the model of the last package used stays loaded, and is replaced when
 * another package's voice speaks. A render cancelled while the model speaks stops after the sentence being spoken.
 */
@Singleton
class SherpaOnnxEngine
    @Inject
    constructor(
        private val store: VoicePackageStore,
        private val runtime: NeuralRuntime,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SpeechEngine {
        private val mutex = Mutex()

        /** The model loaded now and its package; used under [mutex] only. */
        private var loaded: Pair<VoicePackage, NeuralModel>? = null

        /** The neural voices whose package is installed, most recommended first. */
        override suspend fun voices(): List<Voice> {
            val installed = store.installedNow()
            return NeuralVoices.all.filter { it.pkg.id in installed }.map(NeuralVoice::toVoice)
        }

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            output: File,
        ): SynthesisResult {
            val neural = NeuralVoices.byId(voice.id) ?: return UNAVAILABLE
            return mutex.withLock { withContext(dispatcher) { render(neural, text, output) } }
        }

        /** Frees the model of [pkg] when it is loaded, for example before the package is deleted. */
        suspend fun unload(pkg: VoicePackage) {
            mutex.withLock { if (loaded?.first == pkg) release() }
        }

        @Suppress("TooGenericExceptionCaught") // The native layers report their failures as runtime exceptions.
        private suspend fun render(
            voice: NeuralVoice,
            text: String,
            output: File,
        ): SynthesisResult {
            if (!store.isComplete(voice.pkg)) {
                if (loaded?.first == voice.pkg) release()
                return UNAVAILABLE
            }
            return try {
                val job = currentCoroutineContext().job
                val prepared = NeuralText.prepare(text)
                val sentences = phonemes(voice.pkg.family, prepared.text)
                val model = modelOf(voice.pkg)
                // Text without phonemes, such as a lone symbol, becomes a short pause rather than a failure.
                val speech =
                    if (sentences.isEmpty()) {
                        FloatArray(0)
                    } else {
                        model.generate(sentences, voice.speakerId) { job.isActive }
                    }
                job.ensureActive()
                val pcm = NeuralAudio.finish(speech, model.sampleRate, voice.gainDb, prepared.leadingPauseMillis)
                output.parentFile?.mkdirs()
                PcmWav.write(output, pcm, model.sampleRate)
                SynthesisResult.Rendered(pcm.size * MILLIS_PER_SECOND / model.sampleRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeuralRuntimeException) {
                Diagnostics.recordHandled("neural voice", e)
                release()
                SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE)
            } catch (e: IOException) {
                Diagnostics.recordHandled("write neural speech", e)
                SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
            } catch (e: RuntimeException) {
                Diagnostics.recordHandled("neural voice", e)
                SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
            }
        }

        /** The phoneme sentences of [text], merged for Kokoro, without empty ones. */
        private fun phonemes(
            family: ModelFamily,
            text: String,
        ): List<IntArray> {
            val sentences = if (text.isBlank()) emptyList() else runtime.phonemize(text)
            val folded = if (family == ModelFamily.KOKORO) sentences.map(KokoroPhonemes::fold) else sentences
            return folded.filter(IntArray::isNotEmpty)
        }

        private fun modelOf(pkg: VoicePackage): NeuralModel {
            loaded?.let { (loadedPackage, model) -> if (loadedPackage == pkg) return model }
            release()
            return runtime.load(pkg, store.directory(pkg)).also { loaded = pkg to it }
        }

        private fun release() {
            loaded?.second?.close()
            loaded = null
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1_000L
            val UNAVAILABLE = SynthesisResult.Failed(SynthesisFailure.VOICE_UNAVAILABLE)
        }
    }

/** The engine of the neural voices in [Voice.engine]. */
const val NEURAL_ENGINE: String = "sherpa-onnx"

/** android.speech.tts.Voice.QUALITY_VERY_HIGH: neural voices are the best the app has. */
private const val NEURAL_QUALITY = 500

/** This neural voice as a selectable [Voice]: offline, Brazilian Portuguese. */
fun NeuralVoice.toVoice(): Voice =
    Voice(
        id = id,
        engine = NEURAL_ENGINE,
        engineLabel =
            when (pkg.family) {
                ModelFamily.KOKORO -> "Kokoro"
                ModelFamily.PIPER -> "Piper"
            },
        name = name,
        locale = Locale.forLanguageTag("pt-BR"),
        requiresNetwork = false,
        quality = NEURAL_QUALITY,
        slowToRender = slowToRender,
    )
