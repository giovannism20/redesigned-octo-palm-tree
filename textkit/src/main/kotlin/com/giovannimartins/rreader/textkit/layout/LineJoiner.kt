package com.giovannimartins.rreader.textkit.layout

/** Hyphens that can end a line in the middle of a word. Dashes (`–`, `—`) are punctuation and never join words. */
internal object Hyphens {
    val CHARACTERS: Set<Char> = setOf('-', '\u2010')

    /** Whether [text] ends with a hyphen glued to a letter or digit, as in "pala-" or "1990-". */
    fun endsWord(text: CharSequence): Boolean =
        text.length >= 2 && text.last() in CHARACTERS && text[text.length - 2].isLetterOrDigit()
}

/**
 * Joins consecutive lines of one paragraph into its text: with a space, or without one where a word was broken across
 * the lines.
 *
 * - After a soft hyphen the word simply continues.
 * - After a hyphen glued to a letter, a next line that repeats the hyphen (the Portuguese convention for words such as
 *   "guarda-chuva" broken at their own hyphen) is joined keeping one hyphen; a next line that continues in lower case
 *   (or in capitals after capitals) is a syllable break whose hyphen is removed, unless it is Portuguese enclisis or
 *   mesoclisis (see [Enclisis]); a next line starting with a capital letter or a digit keeps the hyphen
 *   ("pré-Amazônia").
 * - After a hyphen glued to a digit the hyphen stays and the number or word continues ("1990-1995").
 * - Anything else is joined with a space.
 */
internal object LineJoiner {
    /** Appends [next], the text of the line that follows [previous], to [paragraph], the text so far. */
    fun append(
        paragraph: StringBuilder,
        previous: TextLine?,
        next: String,
    ) {
        when {
            paragraph.isEmpty() || previous?.softHyphen == true -> paragraph.append(next)
            Hyphens.endsWord(paragraph) -> appendAfterHyphen(paragraph, next)
            else -> paragraph.append(' ').append(next)
        }
    }

    private fun appendAfterHyphen(
        paragraph: StringBuilder,
        next: String,
    ) {
        val fragment = paragraph.subSequence(0, paragraph.length - 1).takeLastWhile(Char::isLetter).toString()
        val first = next.first()
        when {
            first in Hyphens.CHARACTERS && next.getOrNull(1)?.isLetter() == true -> {
                paragraph.append(next, 1, next.length)
            }

            fragment.isNotEmpty() && continuesWord(fragment, next) -> {
                joinBrokenWord(paragraph, fragment, next)
            }

            first.isLetterOrDigit() -> {
                paragraph.append(next)
            }

            else -> {
                paragraph.append(' ').append(next)
            }
        }
    }

    /** A syllable break continues in lower case, or in capitals when the broken word is written in capitals. */
    private fun continuesWord(
        fragment: String,
        next: String,
    ): Boolean {
        val continuation = next.takeWhile(Char::isLetter)
        return continuation.isNotEmpty() &&
            (continuation.first().isLowerCase() || (fragment.isAllUpperCase() && continuation.isAllUpperCase()))
    }

    private fun joinBrokenWord(
        paragraph: StringBuilder,
        fragment: String,
        next: String,
    ) {
        if (!Enclisis.keepsHyphen(fragment.lowercase(), next.lowercase())) paragraph.setLength(paragraph.length - 1)
        paragraph.append(next)
    }

    private fun String.isAllUpperCase(): Boolean = all(Char::isUpperCase)
}
