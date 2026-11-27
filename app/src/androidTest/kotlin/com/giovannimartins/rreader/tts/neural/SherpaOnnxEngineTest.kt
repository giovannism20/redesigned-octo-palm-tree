package com.giovannimartins.rreader.tts.neural

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giovannimartins.rreader.net.UrlConnectionTransport
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.WavInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real native layer on a phone: piper-phonemize with the shipped Portuguese espeak-ng data and sherpa-onnx speak a
 * sentence with a Piper voice. Uses an installed Piper voice, or downloads Faber (67 MB, from GitHub over the
 * allowlisted path, verified by its SHA-256) when none is installed, so run it on Wi-Fi.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = VoicePackageStore(context, Dispatchers.IO)

    @Test
    fun speaksAPortugueseSentenceWithAPiperVoice() {
        val voice = runBlocking { installedPiperVoice() }
        val engine = SherpaOnnxEngine(store, SherpaOnnxRuntime(context), Dispatchers.IO)
        val output = File(context.cacheDir, "neural-voice-test.wav").apply { delete() }

        val sentence = "Olá! Esta é a voz que vai ler os seus livros."
        val result = runBlocking { engine.synthesize(sentence, voice.toVoice(), output) }

        assertThat(result).isInstanceOf(SynthesisResult.Rendered::class.java)
        val millis = (result as SynthesisResult.Rendered).durationMillis
        assertThat(millis).isIn(1_500L..8_000L)
        assertThat(WavInfo.durationMillis(output)).isEqualTo(millis)
        output.delete()
    }

    private suspend fun installedPiperVoice(): NeuralVoice {
        val piper = NeuralVoices.all.filter { it.pkg.family == ModelFamily.PIPER }
        piper.firstOrNull { store.isComplete(it.pkg) }?.let { return it }
        val downloader =
            VoicePackageDownloader(VoiceModelHttp(UrlConnectionTransport(), Dispatchers.IO), store, Dispatchers.IO)
        val outcome = downloader.download(NeuralVoices.FABER)
        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Installed)
        return piper.first { it.pkg == NeuralVoices.FABER }
    }
}
