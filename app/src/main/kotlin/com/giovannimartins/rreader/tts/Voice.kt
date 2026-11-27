package com.giovannimartins.rreader.tts

import java.util.Locale

/**
 * A selectable speaker. [id] is stable across app restarts and identifies the
 * engine and the engine's own voice name, for example
 * `system:com.google.android.tts:pt-br-x-afs-local`.
 */
data class Voice(
    val id: String,
    /** Engine package (system TTS) or model id (on-device neural voices). */
    val engine: String,
    /** Human label of the engine, for example "Speech Services by Google". */
    val engineLabel: String,
    /** The engine's internal voice name. */
    val name: String,
    val locale: Locale,
    /** True when the engine sends text to a server; such voices are not used by default. */
    val requiresNetwork: Boolean,
    /** Engine-reported quality, higher is better (android.speech.tts.Voice constants). */
    val quality: Int,
    /** True when the voice may render slower than real time, so chapters are rendered ahead while charging. */
    val slowToRender: Boolean = false,
)
