package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.ApplicationScope
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.tts.PcmWav
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [SpeechEngine] for the offline neural voices (ADR 0018), writing WAV files into the same rendering pipeline as the
 * phone's voices (ADR 0003). A segment's text is prepared ([NeuralText]: dialogue dashes become pauses),
 * phonemized in Brazilian Portuguese (Kokoro's phonemes merged as [KokoroPhonemes] says), spoken by the voice's
 * model with its speaker id, and finished by [NeuralAudio] (DC offset removed, edges trimmed, the voice's gain, a
 * pause after it). Renders run one at a time; the model of the last package used stays loaded, and is replaced when
 * another package's voice speaks. A render cancelled while the model speaks stops after the sentence being spoken.
 *
 * Native code can crash the process on some input (SR-4), so every call into it goes through [calls]: a segment whose
 * speech killed the app twice in a row becomes a short pause, and a phonemizer or model that did so at start makes
 * the voice unavailable instead of being tried again. Errors the native layers throw as Java errors (a missing
 * library or method, memory running out) release the model and make the voice unavailable, never a crash.
 *
 * A model takes up to 142 MB of native memory, so it is released once no voice has spoken for [IDLE_RELEASE] (in
 * [scope]), when the system asks the app to free memory ([releaseIfIdle]), and before its package is deleted
 * ([deleting]), which waits for the render in progress so no render loads a package while its files go.
 */
@Singleton
class SherpaOnnxEngine
    @Inject
    constructor(
        private val store: VoicePackageStore,
        private val runtime: NeuralRuntime,
        private val calls: NeuralCallMarkers,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope private val scope: CoroutineScope,
    ) : SpeechEngine {
        private val mutex = Mutex()

        /** The model loaded now and its package; used under [mutex] only. */
        private var loaded: Pair<VoicePackage, NeuralModel>? = null

        /** Releases the model after [IDLE_RELEASE] without renders; restarted by each render, under [mutex]. */
        private var idleRelease: Job? = null

        /** Whether espeak-ng started in this process; used under [mutex] only. */
        private var phonemizerStarted = false

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
            return mutex.withLock {
                idleRelease?.cancel()
                try {
                    withContext(dispatcher) { render(neural, text, output) }
                } finally {
                    idleRelease =
                        scope.launch {
                            delay(IDLE_RELEASE)
                            mutex.withLock { release() }
                        }
                }
            }
        }

        /**
         * Runs [deleteFiles], which deletes [pkg]'s files, between renders and with [pkg]'s model released: the render
         * in progress ends first, and the renders waiting find the package gone instead of loading it while its files
         * go. A crash its model caused as it loaded is forgotten, so the package is tried again once downloaded again.
         */
        suspend fun deleting(
            pkg: VoicePackage,
            deleteFiles: suspend () -> Unit,
        ) {
            mutex.withLock {
                if (loaded?.first == pkg) release()
                calls.forget(NeuralCalls.load(pkg))
                deleteFiles()
            }
        }

        /**
         * Releases the loaded model now, unless a voice is speaking: for when the system asks the app to free memory.
         * Does not wait, so it can run on the main thread.
         */
        fun releaseIfIdle() {
            if (!mutex.tryLock()) return
            try {
                idleRelease?.cancel()
                release()
            } finally {
                mutex.unlock()
            }
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
                val speech = speechOf(voice, text)
                output.parentFile?.mkdirs()
                PcmWav.write(output, speech.pcm, speech.sampleRate)
                SynthesisResult.Rendered(speech.pcm.size * MILLIS_PER_SECOND / speech.sampleRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeuralRuntimeException) {
                unavailable(e)
            } catch (e: LinkageError) {
                unavailable(e)
            } catch (e: OutOfMemoryError) {
                unavailable(e)
            } catch (e: IOException) {
                Diagnostics.recordHandled("write neural speech", e)
                SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
            } catch (e: RuntimeException) {
                Diagnostics.recordHandled("neural voice", e)
                SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
            }
        }

        /** The finished audio of [text] spoken by [voice], or a short pause when speaking it killed the app twice. */
        private suspend fun speechOf(
            voice: NeuralVoice,
            text: String,
        ): Speech {
            val prepared = NeuralText.prepare(text)
            val speak = NeuralCalls.speak(voice, text)
            if (calls.shouldSkip(speak)) {
                Diagnostics.recordSkipped("neural segment")
                val pause = NeuralAudio.finish(FloatArray(0), PAUSE_RATE, voice.gainDb, prepared.leadingPauseMillis)
                return Speech(pause, PAUSE_RATE)
            }
            refuseCallsThatKilled(voice.pkg)
            startPhonemizer()
            val model = modelOf(voice.pkg)
            val job = currentCoroutineContext().job
            val samples =
                calls.run(speak) {
                    prepared.parts.map { part ->
                        job.ensureActive()
                        spoken(model, voice, part) { job.isActive }
                    }
                }
            job.ensureActive()
            val tones = voice.pkg.family.artifactTones(model.sampleRate)
            return Speech(
                NeuralAudio.finish(samples, model.sampleRate, voice.gainDb, prepared.leadingPauseMillis, tones),
                model.sampleRate,
            )
        }

        /**
         * Refuses, before any native call, a voice whose phonemizer or model killed the app twice in a row when it
         * started. Making no call keeps the note that says so, so the next process refuses it too.
         */
        private fun refuseCallsThatKilled(pkg: VoicePackage) {
            val phonemizer = !phonemizerStarted && calls.shouldSkip(NeuralCalls.START_PHONEMIZER)
            val model = loaded?.first != pkg && calls.shouldSkip(NeuralCalls.load(pkg))
            if (phonemizer || model) throw NeuralRuntimeException("The voice stopped the app when it started")
        }

        private fun startPhonemizer() {
            if (phonemizerStarted) return
            calls.run(NeuralCalls.START_PHONEMIZER) { runtime.startPhonemizer() }
            phonemizerStarted = true
        }

        /**
         * [text] spoken by [voice]'s [model]: its phoneme sentences, merged for Kokoro, without empty ones. Text
         * without phonemes, such as a lone symbol, is silence.
         */
        private fun spoken(
            model: NeuralModel,
            voice: NeuralVoice,
            text: String,
            keepGoing: () -> Boolean,
        ): FloatArray {
            val raw = if (text.isBlank()) emptyList() else runtime.phonemize(text)
            val kokoro = voice.pkg.family == ModelFamily.KOKORO
            val sentences = raw.map { if (kokoro) KokoroPhonemes.fold(it) else it }.filter(IntArray::isNotEmpty)
            return if (sentences.isEmpty()) FloatArray(0) else model.generate(sentences, voice.speakerId, keepGoing)
        }

        private fun modelOf(pkg: VoicePackage): NeuralModel {
            loaded?.let { (loadedPackage, model) -> if (loadedPackage == pkg) return model }
            release()
            return calls.run(NeuralCalls.load(pkg)) { runtime.load(pkg, store.directory(pkg)) }.also {
                loaded =
                    pkg to it
            }
        }

        /** The voice cannot speak now: the library, the phonemizer or the model failed, or memory ran out. */
        private fun unavailable(error: Throwable): SynthesisResult {
            Diagnostics.recordHandled("neural voice", error)
            release()
            return SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE)
        }

        private fun release() {
            loaded?.second?.close()
            loaded = null
        }

        /** 16-bit PCM of a segment at [sampleRate]. */
        private class Speech(
            val pcm: ShortArray,
            val sampleRate: Int,
        )

        companion object {
            /** How long a model stays loaded after the last render. */
            val IDLE_RELEASE: Duration = 2.minutes

            private const val MILLIS_PER_SECOND = 1_000L

            /** The sample rate of the pause that replaces a segment the app cannot speak. */
            private const val PAUSE_RATE = 16_000
            private val UNAVAILABLE = SynthesisResult.Failed(SynthesisFailure.VOICE_UNAVAILABLE)
        }
    }

/** The engine of the neural voices in [Voice.engine]. */
const val NEURAL_ENGINE: String = "sherpa-onnx"

/**
 * [Voice.renderVersion] of the neural voices: the espeak-ng data's version, and a revision raised whenever text,
 * phonemes or audio are prepared differently. Revision 3: Kokoro's narrow tones are notched out
 * ([ModelFamily.artifactTones]) and a dialogue dash inside a segment is a pause; audio cached before there were
 * revisions is filed under the voice id alone, so none of it is reused.
 */
const val NEURAL_RENDER_VERSION: String = "${EspeakData.VERSION}+speech-3"

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
        renderVersion = NEURAL_RENDER_VERSION,
    )
