package com.giovannimartins.rreader.tts.neural

/**
 * Text as espeak-ng may safely read it. espeak-ng reads a word in another alphabet with that language's dictionary,
 * and the app ships only the Portuguese data plus the dictionaries Portuguese itself switches to (English, German,
 * French; see app/libs/README.md): a missing dictionary crashes espeak-ng's native code. Letters of other scripts
 * (Greek, Cyrillic, Chinese…) therefore become spaces, so such words are skipped instead of read.
 *
 * The espeak-ng inside piper-phonemize also predates fixes for crashes with crafted text (docs/security.md, known
 * vulnerabilities), such as a word copied without a length bound: control characters become spaces, invisible
 * format characters (a soft hyphen, a zero-width space) are dropped, and a run of more than [MAX_WORD] characters
 * without a space is split.
 */
internal object PhonemizerText {
    private val SAFE_SCRIPTS =
        setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED)

    /**
     * The longest run of characters without a space espeak-ng gets: fixed espeak-ng clamps a word at 160 bytes, and 40
     * characters stay under that in UTF-8 (at most 4 bytes each). Real words are shorter.
     */
    const val MAX_WORD = 40

    fun clean(text: String): String {
        val cleaned = StringBuilder(text.length)
        var word = 0
        text.codePoints().forEach { codePoint ->
            when {
                Character.getType(codePoint) == Character.FORMAT.toInt() -> {
                    Unit
                }

                !isWordCharacter(codePoint) -> {
                    cleaned.append(' ')
                    word = 0
                }

                else -> {
                    if (word == MAX_WORD) {
                        cleaned.append(' ')
                        word = 0
                    }
                    cleaned.appendCodePoint(codePoint)
                    word++
                }
            }
        }
        return cleaned.toString()
    }

    private fun isWordCharacter(codePoint: Int): Boolean =
        Character.UnicodeScript.of(codePoint) in SAFE_SCRIPTS &&
            !Character.isISOControl(codePoint) &&
            !Character.isWhitespace(codePoint)
}
