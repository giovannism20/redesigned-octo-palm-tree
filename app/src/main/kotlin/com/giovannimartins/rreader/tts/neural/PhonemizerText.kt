package com.giovannimartins.rreader.tts.neural

/**
 * Text as espeak-ng may safely read it. espeak-ng reads a word in another alphabet with that language's dictionary,
 * and the app ships only the Portuguese data plus the dictionaries Portuguese itself switches to (English, German,
 * French; see app/libs/README.md): a missing dictionary crashes espeak-ng's native code. Letters of other scripts
 * (Greek, Cyrillic, Chinese…) therefore become spaces, so such words are skipped instead of read.
 */
internal object PhonemizerText {
    private val SAFE_SCRIPTS =
        setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED)

    fun clean(text: String): String {
        val cleaned = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.UnicodeScript.of(codePoint) in SAFE_SCRIPTS) {
                cleaned.appendCodePoint(codePoint)
            } else {
                cleaned.append(' ')
            }
            index += Character.charCount(codePoint)
        }
        return cleaned.toString()
    }
}
