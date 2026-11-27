package com.giovannimartins.rreader.ui.voices

import android.net.Uri
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.TestWav
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.neural.VoiceDownloadState
import com.giovannimartins.rreader.tts.neural.VoiceDownloads
import com.giovannimartins.rreader.tts.neural.VoicePackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Voice downloads that only record what was asked; tests set [states]. */
internal class FakeVoiceDownloads : VoiceDownloads {
    val requests = mutableListOf<Pair<String, Boolean>>()
    val cancelled = mutableListOf<String>()
    override val states = MutableStateFlow<Map<String, VoiceDownloadState>>(emptyMap())

    override suspend fun download(
        pkg: VoicePackage,
        wifiOnly: Boolean,
    ) {
        requests += pkg.id to wifiOnly
        states.update { it + (pkg.id to VoiceDownloadState.Waiting(wifiOnly)) }
    }

    override suspend fun cancel(pkg: VoicePackage) {
        cancelled += pkg.id
        states.update { it - pkg.id }
    }

    override suspend fun discardLeftovers() = Unit
}

/** The phone's engines, with [voices]; samples are short silent WAV files. */
internal class FakePhoneEngine(
    private val voices: List<Voice>,
) : SpeechEngine {
    val spoken = mutableListOf<Pair<String, String>>()

    override suspend fun voices(): List<Voice> = voices

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
    ): SynthesisResult {
        spoken += voice.id to text
        TestWav.write(output, 500)
        return SynthesisResult.Rendered(500)
    }
}

/** A sample player that records what it was asked to play. */
internal class RecordingSamples : VoiceSamplePlayer {
    val played = mutableListOf<Uri>()
    var stops = 0
    var released = false
    private var onEnded: (() -> Unit)? = null

    override fun play(
        uri: Uri,
        onEnded: () -> Unit,
    ) {
        played += uri
        this.onEnded = onEnded
    }

    override fun stop() {
        stops++
        onEnded = null
    }

    override fun release() {
        released = true
    }

    /** The sample playing comes to its end. */
    fun finish() {
        onEnded?.invoke()
        onEnded = null
    }
}
