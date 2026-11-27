package com.giovannimartins.rreader.tts.neural

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A segment's text as a neural voice reads it: [text] after a pause of [leadingPauseMillis]. */
internal data class PreparedText(
    val text: String,
    val leadingPauseMillis: Int,
)

/** How segment text is handed to the neural voices. */
internal object NeuralText {
    /** The pause that replaces a dialogue dash at the start of a segment. */
    const val DIALOGUE_PAUSE_MILLIS = 300

    /** Em dash, en dash and horizontal bar, which open dialogue lines in Portuguese books. */
    private val DIALOGUE_DASHES = setOf('—', '–', '―')

    /**
     * Trims [text] and turns a leading dialogue dash into a pause: read as a symbol it can be spoken as a spurious
     * vowel (voice evaluation, ADR 0018). A hyphen counts as a dash only when a space follows it, so "-5" stays.
     */
    fun prepare(text: String): PreparedText {
        val trimmed = text.trim()
        val dash =
            trimmed.firstOrNull() in DIALOGUE_DASHES ||
                (trimmed.startsWith("-") && trimmed.getOrNull(1)?.isWhitespace() == true)
        return if (dash) PreparedText(trimmed.drop(1).trimStart(), DIALOGUE_PAUSE_MILLIS) else PreparedText(trimmed, 0)
    }
}

/**
 * Kokoro's phoneme symbols. espeak-ng writes affricates and some diphthongs as two symbols, which Kokoro's
 * vocabulary has as one; its reference front end merges them, and the voice evaluation (ADR 0018) found the voices
 * clearest with the same merges for Portuguese: tʃ → ʧ, dʒ → ʤ, aɪ → I, eɪ → A.
 */
internal object KokoroPhonemes {
    private val FOLDS: Map<Pair<Int, Int>, Int> =
        mapOf(
            ('t'.code to 'ʃ'.code) to 'ʧ'.code,
            ('d'.code to 'ʒ'.code) to 'ʤ'.code,
            ('a'.code to 'ɪ'.code) to 'I'.code,
            ('e'.code to 'ɪ'.code) to 'A'.code,
        )

    /** [sentence] (phoneme code points) with the pairs above merged. */
    fun fold(sentence: IntArray): IntArray {
        val folded = ArrayList<Int>(sentence.size)
        var index = 0
        while (index < sentence.size) {
            val merged = sentence.getOrNull(index + 1)?.let { next -> FOLDS[sentence[index] to next] }
            if (merged != null) {
                folded += merged
                index += 2
            } else {
                folded += sentence[index]
                index++
            }
        }
        return folded.toIntArray()
    }
}

/**
 * Turns a neural voice's raw samples into the audio of one segment: a 20 Hz high-pass removes the DC offset Kokoro
 * leaves in its output, silence at both ends is trimmed to [EDGE_MILLIS], the voice's gain brings it to the common
 * level (never past [PEAK_LIMIT]), and fixed pauses go before (a dialogue dash) and after (between segments).
 */
internal object NeuralAudio {
    const val HIGH_PASS_HZ = 20.0
    const val EDGE_MILLIS = 40
    const val SEGMENT_PAUSE_MILLIS = 250
    const val PEAK_LIMIT = 0.98f

    /** About -50 dBFS: quieter samples at the ends count as silence. */
    const val SILENCE_LEVEL = 0.003f

    /** Notch quality: a band of a sixtieth of the tone's frequency. */
    private const val NOTCH_Q = 60.0

    /** Each tone is notched twice: once leaves a few decibels of a tone that is slightly wider than a pure line. */
    private const val NOTCH_PASSES = 2

    private const val DECIBELS_PER_TENFOLD = 20.0
    private const val MILLIS_PER_SECOND = 1_000
    private const val FULL_SCALE = 32_767f

    /**
     * 16-bit PCM of [samples] (in [-1, 1] at [sampleRate]) for a voice with [gainDb], as described above, without the
     * model's narrow [tones] (Hz, see [ModelFamily.artifactTones]).
     */
    fun finish(
        samples: FloatArray,
        sampleRate: Int,
        gainDb: Float,
        leadingPauseMillis: Int,
        tones: List<Double> = emptyList(),
    ): ShortArray {
        val speech = trimmed(highPassed(withoutTones(samples, sampleRate, tones), sampleRate), sampleRate)
        val peak = speech.maxOfOrNull { abs(it) } ?: 0f
        val gain = 10.0.pow(gainDb / DECIBELS_PER_TENFOLD).toFloat()
        val scale = if (peak > 0f) minOf(gain, PEAK_LIMIT / peak) else gain
        val lead = samplesFor(leadingPauseMillis, sampleRate)
        val pcm = ShortArray(lead + speech.size + samplesFor(SEGMENT_PAUSE_MILLIS, sampleRate))
        speech.forEachIndexed { index, value -> pcm[lead + index] = toPcm(value * scale) }
        return pcm
    }

    /**
     * [samples] without each of [tones] (Hz): every tone goes through [NOTCH_PASSES] narrow notch filters, which take
     * it below the background while the sound around it changes by less than 0.1 dB.
     */
    fun withoutTones(
        samples: FloatArray,
        sampleRate: Int,
        tones: List<Double>,
    ): FloatArray {
        var out = samples
        tones.forEach { tone -> repeat(NOTCH_PASSES) { out = notched(out, sampleRate, tone) } }
        return out
    }

    /** A biquad notch filter (the audio cookbook's) at [frequency], [NOTCH_Q] wide: 80 Hz at 4.8 kHz. */
    private fun notched(
        samples: FloatArray,
        sampleRate: Int,
        frequency: Double,
    ): FloatArray {
        val w0 = 2.0 * PI * frequency / sampleRate
        val alpha = sin(w0) / (2.0 * NOTCH_Q)
        val a0 = 1.0 + alpha
        val b0 = 1.0 / a0
        val b1 = -2.0 * cos(w0) / a0
        val a2 = (1.0 - alpha) / a0
        val out = FloatArray(samples.size)
        var in1 = 0.0
        var in2 = 0.0
        var out1 = 0.0
        var out2 = 0.0
        samples.forEachIndexed { index, value ->
            // b2 equals b0 and a1 equals b1 for a notch.
            val y = b0 * value + b1 * in1 + b0 * in2 - b1 * out1 - a2 * out2
            in2 = in1
            in1 = value.toDouble()
            out2 = out1
            out1 = y
            out[index] = y.toFloat()
        }
        return out
    }

    private fun toPcm(value: Float): Short = (value.coerceIn(-1f, 1f) * FULL_SCALE).roundToInt().toShort()

    /** A one-pole DC blocker with its cut-off at [HIGH_PASS_HZ]. */
    fun highPassed(
        samples: FloatArray,
        sampleRate: Int,
    ): FloatArray {
        val pole = (1.0 - 2.0 * PI * HIGH_PASS_HZ / sampleRate).toFloat()
        val out = FloatArray(samples.size)
        var previousIn = 0f
        var previousOut = 0f
        samples.forEachIndexed { index, value ->
            previousOut = value - previousIn + pole * previousOut
            previousIn = value
            out[index] = previousOut
        }
        return out
    }

    /** [samples] without the silence at both ends beyond [EDGE_MILLIS]; empty when everything is silent. */
    private fun trimmed(
        samples: FloatArray,
        sampleRate: Int,
    ): FloatArray {
        val first = samples.indexOfFirst { abs(it) > SILENCE_LEVEL }
        if (first < 0) return FloatArray(0)
        val last = samples.indexOfLast { abs(it) > SILENCE_LEVEL }
        val edge = samplesFor(EDGE_MILLIS, sampleRate)
        return samples.copyOfRange((first - edge).coerceAtLeast(0), (last + 1 + edge).coerceAtMost(samples.size))
    }

    private fun samplesFor(
        millis: Int,
        sampleRate: Int,
    ): Int = (millis.toLong() * sampleRate / MILLIS_PER_SECOND).toInt()
}
