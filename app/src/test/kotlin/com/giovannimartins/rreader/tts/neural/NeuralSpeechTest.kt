package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.tts.PcmWav
import com.giovannimartins.rreader.tts.WavInfo
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    fun `Kokoro leaves tones at multiples of a fifth of its sample rate, Piper none`() {
        assertThat(ModelFamily.KOKORO.artifactTones(24_000)).containsExactly(4_800.0, 9_600.0).inOrder()
        assertThat(ModelFamily.PIPER.artifactTones(22_050)).isEmpty()
    }

    @Test
    fun `the model's tones are removed and the sound around them is kept`() {
        val rate = 24_000
        val signal =
            FloatArray(2 * rate) { index ->
                val t = index.toDouble() / rate
                val speechLike =
                    0.2 * sin(2 * PI * 1_000 * t) + 0.2 * sin(2 * PI * 4_500 * t) + 0.2 * sin(2 * PI * 7_000 * t)
                val whistle = 0.05 * sin(2 * PI * 4_800 * t) + 0.05 * sin(2 * PI * 9_600 * t)
                (speechLike + whistle).toFloat()
            }

        val cleaned = NeuralAudio.withoutTones(signal, rate, ModelFamily.KOKORO.artifactTones(rate))

        // Measured after the filters settle (0.1 s).
        fun ratio(frequency: Double) = magnitude(cleaned, rate, frequency) / magnitude(signal, rate, frequency)
        assertThat(ratio(4_800.0)).isLessThan(0.03)
        assertThat(ratio(9_600.0)).isLessThan(0.03)
        listOf(1_000.0, 4_500.0, 7_000.0).forEach { frequency ->
            assertWithMessage("$frequency Hz").that(ratio(frequency)).isWithin(0.02).of(1.0)
        }
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

    /** The magnitude of [frequency] in [samples] after their first tenth of a second (the Goertzel algorithm). */
    private fun magnitude(
        samples: FloatArray,
        rate: Int,
        frequency: Double,
    ): Double {
        val coefficient = 2 * cos(2 * PI * frequency / rate)
        var previous = 0.0
        var beforePrevious = 0.0
        for (index in rate / 10 until samples.size) {
            val current = samples[index] + coefficient * previous - beforePrevious
            beforePrevious = previous
            previous = current
        }
        return sqrt(previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious)
    }
}
