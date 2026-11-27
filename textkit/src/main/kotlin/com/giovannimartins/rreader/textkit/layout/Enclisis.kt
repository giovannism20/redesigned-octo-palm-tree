package com.giovannimartins.rreader.textkit.layout

/**
 * Decides whether the hyphen at the end of a line belongs to the word because it joins a Portuguese verb to an
 * unstressed pronoun after it (enclisis, "fazê-lo", "dá-lhe") or inside it (mesoclisis, "amá-lo-ei"), so that joining
 * the lines must keep it.
 *
 * Without a dictionary, a syllable break such as "noi-te", "capítu-lo" or "me-nos" looks exactly like enclisis, and
 * such breaks are far more frequent. The hyphen is therefore kept only when the next line starts with a pronoun (`o`,
 * `a`, `os`, `as`, `lo`, `la`, `los`, `las`, `no`, `na`, `nos`, `nas`, `lhe`, `lhes`, `me`, `te`, `se`, `vos`) and
 * the fragment before the hyphen ends the way verb forms do before that pronoun:
 * - any fragment before `o`, `a`, `os` and `as`, because hyphenation never leaves a lone vowel at the start of a line
 *   ("deixou-o");
 * - an accented vowel, except before `no`, `na` and `nas` ("fazê-lo", "dá-lhe", "pô-la", "dê-nos"; but "ruí-na");
 * - `m`, `z`, `ão`, `õe`, or the past tense endings `ou`, `eu` and `iu` ("fazem-no", "fez-se", "dão-lhe", "põe-se",
 *   "levantou-se", "deu-lhe", "pediu-me");
 * - `ei`, `oi`, `ai` and `ui`, except before `te` ("deixei-me", "foi-se"; but "noi-te", "lei-te");
 * - the imperfect endings `ia` and `ava` before `se`, `me`, `te`, `lhe`, `lhes` and `vos` ("dizia-se", "olhava-me";
 *   but "italia-nos", "cava-lo");
 * - `r` before `lhe`, `lhes` and `se` ("dizer-lhe", "tornar-se"; but "par-te", "fir-me"), and before `me`, `te`,
 *   `nos` and `vos` when another hyphen follows the pronoun (mesoclisis: "dir-te-ia");
 * - `e` and `i` before `lhe` and `lhes` ("disse-lhe", "pedi-lhe"; but "peque-no").
 *
 * Any other break is a syllable break whose hyphen is removed ("capítu-lo" becomes "capítulo"); rare enclisis such as
 * "parti-lo" then loses its hyphen.
 */
internal object Enclisis {
    private val VOWEL_PRONOUNS = setOf("o", "a", "os", "as")
    private val NASAL_PRONOUNS = setOf("no", "na", "nas")
    private val IMPERFECT_PRONOUNS = setOf("se", "me", "te", "lhe", "lhes", "vos")
    private val INFINITIVE_PRONOUNS = setOf("lhe", "lhes", "se")
    private val MESOCLITIC_PRONOUNS = setOf("me", "te", "nos", "vos")
    private val INDIRECT_PRONOUNS = setOf("lhe", "lhes")
    private val PRONOUNS =
        VOWEL_PRONOUNS + NASAL_PRONOUNS + IMPERFECT_PRONOUNS + INFINITIVE_PRONOUNS + MESOCLITIC_PRONOUNS +
            setOf("lo", "la", "los", "las")

    private const val ACCENTED_VOWELS = "áâéêíóôú"
    private val ANY_PRONOUN_ENDINGS = listOf("ão", "õe", "ou", "eu", "iu", "m", "z")
    private val FALLING_DIPHTHONGS = listOf("ei", "oi", "ai", "ui")
    private val IMPERFECT_ENDINGS = listOf("ia", "ava")

    /**
     * Whether the hyphen between [fragment], the letters before the hyphen at the end of a line, and [continuation],
     * the text of the next line, is part of the word. Both must be in lower case.
     */
    fun keepsHyphen(
        fragment: String,
        continuation: String,
    ): Boolean {
        val pronoun = continuation.takeWhile(Char::isLetter)
        return pronoun in PRONOUNS && fragment.isNotEmpty() && endsLikeVerb(fragment, pronoun, continuation)
    }

    private fun endsLikeVerb(
        fragment: String,
        pronoun: String,
        continuation: String,
    ): Boolean =
        when {
            pronoun in VOWEL_PRONOUNS -> true
            fragment.last() in ACCENTED_VOWELS -> pronoun !in NASAL_PRONOUNS
            ANY_PRONOUN_ENDINGS.any(fragment::endsWith) -> true
            FALLING_DIPHTHONGS.any(fragment::endsWith) -> pronoun != "te"
            IMPERFECT_ENDINGS.any(fragment::endsWith) -> pronoun in IMPERFECT_PRONOUNS
            fragment.endsWith('r') -> pronoun in INFINITIVE_PRONOUNS || isMesoclisis(pronoun, continuation)
            fragment.endsWith('e') || fragment.endsWith('i') -> pronoun in INDIRECT_PRONOUNS
            else -> false
        }

    /** The pronoun is followed by another hyphen and a verb ending, as in "dir-te-ia". */
    private fun isMesoclisis(
        pronoun: String,
        continuation: String,
    ): Boolean =
        pronoun in MESOCLITIC_PRONOUNS &&
            continuation.getOrNull(pronoun.length) in Hyphens.CHARACTERS &&
            continuation.getOrNull(pronoun.length + 1)?.isLetter() == true
}
