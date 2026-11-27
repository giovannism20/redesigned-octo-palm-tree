package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.tts.PcmWav
import com.giovannimartins.rreader.tts.WavInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class NeuralSpeechTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a leading dialogue dash becomes a pause and the rest is read`() {
        assertThat(NeuralText.prepare("— Capítulo 12.")).isEqualTo(PreparedText("Capítulo 12.", 300))
        assertThat(NeuralText.prepare("  – Sim, disse ele.")).isEqualTo(PreparedText("Sim, disse ele.", 300))
        assertThat(NeuralText.prepare("- Venha cá.")).isEqualTo(PreparedText("Venha cá.", 300))
        assertThat(NeuralText.prepare("-5 graus lá fora.")).isEqualTo(PreparedText("-5 graus lá fora.", 0))
        assertThat(
            NeuralText.prepare("Uma noite — disse ele — fria."),
        ).isEqualTo(PreparedText("Uma noite — disse ele — fria.", 0))
    }

    @Test
    fun `Kokoro's affricates and diphthongs become single symbols`() {
        val raw = "nˈoɪtʃy sidˈadʒy ˌeɪŋkoŋtrˈeɪ bˈaɪxʊ"
        val expected = "nˈoɪʧy sidˈaʤy ˌAŋkoŋtrˈA bˈIxʊ"

        assertThat(
            KokoroPhonemes.fold(raw.codePoints().toArray()).toList(),
        ).isEqualTo(expected.codePoints().toArray().toList())
        assertThat(KokoroPhonemes.fold(intArrayOf('t'.code)).toList()).containsExactly('t'.code)
        assertThat(KokoroPhonemes.fold(IntArray(0)).toList()).isEmpty()
    }

    @Test
    fun `the DC offset is removed`() {
        val rate = 24_000
        val offset = FloatArray(rate) { 0.025f + 0.3f * sin(2 * PI * 200 * it / rate).toFloat() }

        val filtered = NeuralAudio.highPassed(offset, rate)

        val lastHalf = filtered.copyOfRange(rate / 2, rate)
        assertThat(abs(lastHalf.average())).isLessThan(0.001)
        assertThat(lastHalf.maxOrNull()!!).isWithin(0.03f).of(0.3f)
    }

    @Test
    fun `silent edges are trimmed and fixed pauses added around the speech`() {
        val rate = 10_000
        val speech = FloatArray(1_000) { 0.5f * sin(2 * PI * 300 * it / rate).toFloat() }
        val raw = FloatArray(5_000) + speech + FloatArray(5_000)

        val pcm = NeuralAudio.finish(raw, rate, gainDb = 0f, leadingPauseMillis = 300)

        val edge = NeuralAudio.EDGE_MILLIS * rate / 1_000
        val lead = 300 * rate / 1_000
        val tail = NeuralAudio.SEGMENT_PAUSE_MILLIS * rate / 1_000
        assertThat(pcm.size).isWithin(2 * edge + 4).of(lead + speech.size + 2 * edge + tail)
        assertThat(pcm.take(lead).all { it == 0.toShort() }).isTrue()
        assertThat(pcm.takeLast(tail).all { it == 0.toShort() }).isTrue()
    }

    @Test
    fun `the voice's gain is applied without going past the peak limit`() {
        val rate = 10_000
        val quiet = FloatArray(2_000) { 0.1f * sin(2 * PI * 300 * it / rate).toFloat() }

        val louder = NeuralAudio.finish(quiet, rate, gainDb = 6f, leadingPauseMillis = 0)
        val limited = NeuralAudio.finish(quiet, rate, gainDb = 30f, leadingPauseMillis = 0)

        assertThat(louder.maxOf { abs(it.toInt()) } / 32_767.0).isWithin(0.02).of(0.2)
        assertThat(limited.maxOf { abs(it.toInt()) } / 32_767.0).isWithin(0.01).of(NeuralAudio.PEAK_LIMIT.toDouble())
    }

    @Test
    fun `silence alone becomes just the pause`() {
        val pcm = NeuralAudio.finish(FloatArray(500), 10_000, gainDb = 3f, leadingPauseMillis = 0)

        assertThat(pcm.size).isEqualTo(NeuralAudio.SEGMENT_PAUSE_MILLIS * 10)
        assertThat(pcm.all { it == 0.toShort() }).isTrue()
    }

    @Test
    fun `written speech is a WAV file whose length the player reads`() {
        val file = File(folder.root, "speech.wav")

        PcmWav.write(file, ShortArray(22_050) { (it % 100).toShort() }, 22_050)

        assertThat(WavInfo.durationMillis(file)).isEqualTo(1_000)
        assertThat(file.length()).isEqualTo(44L + 2 * 22_050)
    }
}
