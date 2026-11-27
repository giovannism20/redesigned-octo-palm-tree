package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.TextRange

/**
 * Cuts sentences longer than [maxLength] into pieces of at most [maxLength] characters.
 *
 * Each cut is made at the best break that keeps the piece within the limit: the last `;`, then `:`, then
 * spaced dash (` — `), then `,` found in the second half of the allowed length; failing that, the last
 * space; failing that (a single huge "word"), at the limit itself, never inside a surrogate pair or before a
 * combining mark. Pieces are trimmed and together cover every non-whitespace character of the sentence.
 */
internal class LongSentenceSplitter(
    private val maxLength: Int,
) {
    private val minPieceLength = (maxLength / 2).coerceAtLeast(1)

    /** Splits [sentence], a trimmed range of [text], into pieces no longer than [maxLength]. */
    fun split(
        text: String,
        sentence: TextRange,
    ): List<TextRange> {
        val pieces = mutableListOf<TextRange>()
        var start = sentence.start
        while (sentence.end - start > maxLength) {
            val cut = nextCut(text, start)
            pieces += TextRange(start, text.trimEndIndex(start, cut.leftEnd))
            start = text.skipSpaces(cut.rightStart, sentence.end)
        }
        pieces += TextRange(start, sentence.end)
        return pieces
    }

    /** Where to cut the text that starts at [start] and is known to run past `start + maxLength`. */
    private fun nextCut(
        text: String,
        start: Int,
    ): Cut {
        val limit = start + maxLength
        return PREFERRED_BREAKS.firstNotNullOfOrNull { lastCut(it, text, start + minPieceLength, limit) }
            ?: lastCut(Break.SPACE, text, start + 1, limit)
            ?: hardCut(text, start, limit)
    }

    /** The cut of [kind] whose left piece ends latest within `[from, limit]`. */
    private fun lastCut(
        kind: Break,
        text: String,
        from: Int,
        limit: Int,
    ): Cut? = (limit downTo from).firstNotNullOfOrNull { leftEnd -> kind.cutAt(text, leftEnd) }

    private fun hardCut(
        text: String,
        start: Int,
        limit: Int,
    ): Cut {
        var cut = limit
        while (cut > start + 1 && !isSafeCut(text[cut])) cut--
        return Cut(cut, cut)
    }

    /** Whether a piece may start with [char]: not the second half of a surrogate pair nor a combining mark. */
    private fun isSafeCut(char: Char): Boolean = !char.isLowSurrogate() && Character.getType(char) !in COMBINING_MARKS

    /** A cut: the left piece ends before [leftEnd] and the right piece starts at or after [rightStart]. */
    private data class Cut(
        val leftEnd: Int,
        val rightStart: Int,
    )

    private enum class Break {
        SEMICOLON,
        COLON,
        DASH,
        COMMA,
        SPACE,
        ;

        /**
         * The cut of this kind whose left piece ends at [leftEnd], or null. Callers guarantee that
         * `text[leftEnd]` exists and that `leftEnd` is past the start of the piece.
         */
        fun cutAt(
            text: String,
            leftEnd: Int,
        ): Cut? =
            when (this) {
                SEMICOLON -> punctuationCut(text, leftEnd, ';')
                COLON -> punctuationCut(text, leftEnd, ':')
                COMMA -> punctuationCut(text, leftEnd, ',')
                DASH -> if (isSpacedDash(text, leftEnd)) Cut(leftEnd, leftEnd + 1) else null
                SPACE -> if (text[leftEnd].isWhitespace()) Cut(leftEnd, leftEnd + 1) else null
            }

        /** `mark` followed by a space: cut after the mark, so `10,50` or `10:30` never break. */
        private fun punctuationCut(
            text: String,
            leftEnd: Int,
            mark: Char,
        ): Cut? = if (text[leftEnd - 1] == mark && text[leftEnd].isWhitespace()) Cut(leftEnd, leftEnd) else null

        /** ` — ` starting at [space]: cut before the dash, which opens the next piece. */
        private fun isSpacedDash(
            text: String,
            space: Int,
        ): Boolean =
            space + 2 < text.length &&
                text[space + 1] in DASHES &&
                (text[space].isWhitespace() && text[space + 2].isWhitespace())
    }

    private companion object {
        val PREFERRED_BREAKS = listOf(Break.SEMICOLON, Break.COLON, Break.DASH, Break.COMMA)

        /** Dashes that separate clauses when surrounded by spaces. */
        const val DASHES = "—–-"

        val COMBINING_MARKS =
            setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
            )
    }
}
