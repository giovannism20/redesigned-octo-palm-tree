package com.giovannimartins.rreader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.giovannimartins.rreader.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * [SpeechEngine] backed by the Android text-to-speech engines installed on the
 * phone (Google, Samsung, …). Free and, with offline voices, fully on-device.
 * One [TextToSpeech] client is kept per engine package; calls are serialized
 * because engines synthesize one utterance at a time.
 */
@Singleton
class SystemTtsEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : SpeechEngine {
        private val mutex = Mutex()
        private val clients = mutableMapOf<String, TextToSpeech>()
        private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        override suspend fun voices(): List<Voice> =
            mutex.withLock {
                val probe = client(DEFAULT_ENGINE) ?: return@withLock emptyList()
                probe.engines
                    .flatMap { engine ->
                        val tts = client(engine.name) ?: return@flatMap emptyList()
                        tts.voices
                            .orEmpty()
                            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
                            .map { voice ->
                                Voice(
                                    id = VoiceIds.system(engine.name, voice.name),
                                    engine = engine.name,
                                    engineLabel = engine.label,
                                    name = voice.name,
                                    locale = voice.locale,
                                    requiresNetwork = voice.isNetworkConnectionRequired,
                                    quality = voice.quality,
                                )
                            }
                    }.sortedWith(VOICE_ORDER)
            }

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            output: File,
            speed: Float,
        ): SynthesisResult =
            mutex.withLock {
                val tts = client(voice.engine)
                val target = tts?.voices?.firstOrNull { it.name == voice.name }
                when {
                    tts == null -> SynthesisResult.Failed(SynthesisFailure.ENGINE_UNAVAILABLE)
                    target == null -> SynthesisResult.Failed(SynthesisFailure.VOICE_UNAVAILABLE)
                    else -> render(tts, target, text, output)
                }
            }

        /** Renders one utterance to [output] and waits for the engine to finish writing it. */
        private suspend fun render(
            tts: TextToSpeech,
            target: android.speech.tts.Voice,
            text: String,
            output: File,
        ): SynthesisResult {
            withContext(io) { output.parentFile?.mkdirs() }
            val utteranceId = UUID.randomUUID().toString()
            val done = CompletableDeferred<Boolean>()
            pending[utteranceId] = done
            val started =
                withContext(Dispatchers.Main) {
                    tts.voice = target
                    tts.synthesizeToFile(text, Bundle(), output, utteranceId) == TextToSpeech.SUCCESS
                }
            val finished = started && (withTimeoutOrNull(SYNTHESIS_TIMEOUT) { done.await() } ?: false)
            pending.remove(utteranceId)
            val duration = if (finished) withContext(io) { WavInfo.durationMillis(output) } else null
            return when {
                !started -> SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
                !finished && done.isActive -> SynthesisResult.Failed(SynthesisFailure.TIMEOUT)
                duration == null || duration <= 0 -> SynthesisResult.Failed(SynthesisFailure.RENDER_ERROR)
                else -> SynthesisResult.Rendered(duration)
            }
        }

        /** Releases every engine connection. */
        fun shutdown() {
            clients.values.forEach(TextToSpeech::shutdown)
            clients.clear()
        }

        /** Returns a ready client for [enginePackage] ([DEFAULT_ENGINE] = system default), creating it once. */
        private suspend fun client(enginePackage: String): TextToSpeech? =
            clients[enginePackage] ?: createClient(enginePackage)?.also { tts ->
                tts.setOnUtteranceProgressListener(progressListener)
                clients[enginePackage] = tts
            }

        private suspend fun createClient(enginePackage: String): TextToSpeech? =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<TextToSpeech?> { continuation ->
                    val holder = arrayOfNulls<TextToSpeech>(1)
                    val listener =
                        TextToSpeech.OnInitListener { status ->
                            val tts = holder[0]
                            if (status == TextToSpeech.SUCCESS && tts != null) {
                                continuation.resume(tts)
                            } else {
                                tts?.shutdown()
                                continuation.resume(null)
                            }
                        }
                    holder[0] =
                        if (enginePackage == DEFAULT_ENGINE) {
                            TextToSpeech(context, listener)
                        } else {
                            TextToSpeech(context, listener, enginePackage)
                        }
                }
            }

        private val progressListener =
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pending[it]?.complete(true) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { pending[it]?.complete(false) }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    utteranceId?.let { pending[it]?.complete(false) }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) {
                    utteranceId?.let { pending[it]?.complete(false) }
                }
            }

        companion object {
            /** Offline voices first, then higher quality, then a stable order by name. */
            private val VOICE_ORDER =
                compareBy<Voice> { it.requiresNetwork }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            private const val DEFAULT_ENGINE = ""
            private val SYNTHESIS_TIMEOUT = 60.seconds
        }
    }
