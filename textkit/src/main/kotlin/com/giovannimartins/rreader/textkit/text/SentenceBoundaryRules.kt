package com.giovannimartins.rreader.textkit.text

import java.text.Normalizer

/**
 * Decides whether a terminator cluster ends a sentence. The rules are documented on [SentenceSegmenter].
 *
 * @property portuguese whether Portuguese abbreviations and sentence starters apply.
 */
internal class SentenceBoundaryRules(
    private val portuguese: Boolean,
) {
    /**
     * Whether [cluster] ends the sentence that started at [sentenceStart], given that the next sentence
     * would start at [next]. Indices are into [line], which holds no line breaks.
     */
    fun endsSentence(
        line: String,
        sentenceStart: Int,
        cluster: TerminatorCluster,
        next: Int,
    ): Boolean {
        val nextWord = NextWord.at(line, next)
        return when {
            // Spaced dots, as in `pensou . . . e saiu`, are one mark.
            line[next] in Punctuation.TERMINATORS -> false

            nextWord.startsInLowercase -> false

            cluster.isSinglePeriod(line) -> !periodContinues(line, sentenceStart, cluster.start, nextWord)

            else -> true
        }
    }

    /** Whether the single period at [period] belongs to an abbreviation, initial or list marker. */
    private fun periodContinues(
        line: String,
        sentenceStart: Int,
        period: Int,
        next: NextWord,
    ): Boolean {
        val tokenStart = tokenStart(line, period)
        val token = Normalizer.normalize(line.substring(tokenStart, period), Normalizer.Form.NFC)
        return when {
            token.isEmpty() || isEndOfHyphenatedWord(line, tokenStart) -> false
            tokenStart == sentenceStart && isListMarker(token) -> true
            token in PortugueseAbbreviations.DOTTED_SUFFIXES -> false
            portuguese && token.lowercase() in PortugueseAbbreviations.PREFIXES -> true
            INITIALS.matches(token) -> !(portuguese && token.length == 1 && next.isSentenceStarter)
            else -> false
        }
    }

    /** Start of the word (letters, digits and inner periods, as in `a.C`) that ends at [period]. */
    private fun tokenStart(
        line: String,
        period: Int,
    ): Int {
        var start = period
        while (start > 0 && isTokenChar(line[start - 1])) start--
        while (start < period && line[start] == '.') start++
        return start
    }

    private fun isTokenChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '.' || Character.getType(char) == Character.NON_SPACING_MARK.toInt()

    /** `encontrei-o.` or `fazê-lo.`: the token is the tail of a hyphenated word, never an abbreviation. */
    private fun isEndOfHyphenatedWord(
        line: String,
        tokenStart: Int,
    ): Boolean = tokenStart >= 2 && line[tokenStart - 1] == '-' && line[tokenStart - 2].isLetter()

    /** `1.`, `12.` or `IV.` opening a list item or a numbered section. */
    private fun isListMarker(token: String): Boolean =
        (token.length <= MAX_LIST_NUMBER_DIGITS && token.all(Char::isDigit)) || RomanNumerals.parse(token) != null

    private companion object {
        /** Single letters, optionally joined by periods: `J`, `J.M`. */
        val INITIALS = Regex("[A-Za-z](?:\\.[A-Za-z])*")

        const val MAX_LIST_NUMBER_DIGITS = 3
    }
}

/** A run of sentence terminators with the closing quotes and brackets that follow it, as in `?!»`. */
internal data class TerminatorCluster(
    val start: Int,
    val end: Int,
) {
    /** Whether the cluster is a lone period, the only terminator that abbreviations and initials use. */
    fun isSinglePeriod(line: CharSequence): Boolean = end - start == 1 && line[start] == '.'

    companion object {
        /** The cluster that starts with the terminator at [start] in [line]. */
        fun at(
            line: CharSequence,
            start: Int,
        ): TerminatorCluster {
            var end = start + 1
            while (end < line.length) {
                val extended = extend(line, end)
                if (extended == end) break
                end = extended
            }
            return TerminatorCluster(start, end)
        }

        private fun extend(
            line: CharSequence,
            index: Int,
        ): Int {
            val char = line[index]
            return when {
                char in Punctuation.TERMINATORS || char in Punctuation.CLOSERS -> {
                    index + 1
                }

                char.isWhitespace() -> {
                    val closer = line.skipSpaces(index)
                    if (closer < line.length && line[closer] in Punctuation.SPACED_CLOSERS) closer + 1 else index
                }

                else -> {
                    index
                }
            }
        }
    }
}

/** The first word after a terminator, once opening quotes, brackets and dialogue dashes are skipped. */
internal class NextWord private constructor(
    private val first: Char?,
    private val word: String,
    private val endsWithPeriod: Boolean,
) {
    /** Lowercase means the sentence goes on: `Quem? — perguntou ele`, `pensou... e saiu`, `etc. e`. */
    val startsInLowercase: Boolean get() = first?.isLowerCase() == true

    /** Whether the word usually opens a Portuguese sentence and is not itself abbreviated. */
    val isSentenceStarter: Boolean get() = !endsWithPeriod && word in PortugueseAbbreviations.SENTENCE_STARTERS

    companion object {
        /** The next word at or after [index] in [line]. */
        fun at(
            line: String,
            index: Int,
        ): NextWord {
            var start = index
            while (start < line.length && (line[start] in Punctuation.OPENERS || line[start].isWhitespace())) start++
            var end = start
            while (end < line.length && line[end].isLetter()) end++
            return NextWord(line.getOrNull(start), line.substring(start, end), line.getOrNull(end) == '.')
        }
    }
}
