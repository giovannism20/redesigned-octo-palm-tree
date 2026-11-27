package com.giovannimartins.rreader.tts

/**
 * The kinds of voice ids kept in the settings and on books: a phone voice (`system:<engine>:<name>`), an offline
 * neural voice (`neural:<key>`), or [PHONE], the phone's own Portuguese voice chosen automatically.
 */
object VoiceIds {
    /** "The phone's voice": the best offline Portuguese voice of the system engines, whichever it is today. */
    const val PHONE: String = "phone"

    private const val NEURAL_PREFIX = "neural:"
    private const val SYSTEM_PREFIX = "system:"

    /** The id of the neural voice [key], such as `neural:dora`. */
    fun neural(key: String): String = NEURAL_PREFIX + key

    /** The key of a neural voice id, or null when [voiceId] is not one. */
    fun neuralKey(voiceId: String?): String? =
        voiceId?.takeIf { it.startsWith(NEURAL_PREFIX) }?.removePrefix(NEURAL_PREFIX)

    /** The id of voice [name] of the system engine [enginePackage], such as `system:com.example.tts:pt-br-x-1`. */
    fun system(
        enginePackage: String,
        name: String,
    ): String = "$SYSTEM_PREFIX$enginePackage:$name"

    /** Whether [voiceId] names one of the phone's own voices. */
    fun isSystem(voiceId: String?): Boolean = voiceId?.startsWith(SYSTEM_PREFIX) == true
}
