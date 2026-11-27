package com.giovannimartins.rreader.ui.voices

import com.giovannimartins.rreader.tts.Voice

/** A phone voice as the Voices screen names it: "[engine] · Voice [number]", numbered within its engine. */
data class PhoneVoiceLabel(
    val voice: Voice,
    val engine: String,
    val number: Int,
)

/**
 * Names the phone's voices so that no two look the same: by the short name of their engine and their number in it,
 * in the engines' own order (offline first, then quality). Two engines with the same short name keep their full
 * system label instead, and when even those are the same the package tells them apart.
 */
object PhoneVoiceLabels {
    /** Short names of well-known engine packages; other engines go by the label the system gives them. */
    private val SHORT_NAMES = mapOf("com.google.android.tts" to "Google", "com.samsung.SMT" to "Samsung")

    fun of(voices: List<Voice>): List<PhoneVoiceLabel> {
        val engines = voices.map(Voice::engine).distinct()
        val names = distinctNames(engines, voices)
        val counts = mutableMapOf<String, Int>()
        return voices.map { voice ->
            val number = counts.merge(voice.engine, 1, Int::plus) ?: 1
            PhoneVoiceLabel(voice, names.getValue(voice.engine), number)
        }
    }

    private fun distinctNames(
        engines: List<String>,
        voices: List<Voice>,
    ): Map<String, String> {
        val label = { engine: String -> voices.first { it.engine == engine }.engineLabel.ifBlank { engine } }
        val short = engines.associateWith { SHORT_NAMES[it] ?: label(it) }
        val full =
            short.mapValues { (engine, name) ->
                if (short.values.count { it == name } >
                    1
                ) {
                    label(engine)
                } else {
                    name
                }
            }
        return full.mapValues { (engine, name) ->
            if (full.values.count { it == name } >
                1
            ) {
                "$name ($engine)"
            } else {
                name
            }
        }
    }
}
