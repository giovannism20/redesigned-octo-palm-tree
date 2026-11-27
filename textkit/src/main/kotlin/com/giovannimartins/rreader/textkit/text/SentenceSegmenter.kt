package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.TextRange
import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text, usually a chapter's, into sentences: the units that [SegmentPlanner] groups into segments
 * and that playback resumes from.
 *
 * For any input, the returned ranges:
 * - never cross a line break, so never a paragraph boundary
 *   ([com.giovannimartins.rreader.textkit.model.Chapter.PARAGRAPH_SEPARATOR]);
 * - are trimmed, non-empty, ordered and non-overlapping, and together cover every non-whitespace character;
 * - are at most [maxSentenceLength] characters long.
 *
 * The locale's [BreakIterator] proposes boundaries, but boundaries after `.`, `!`, `?`, `…` (with any closing
 * quotes or brackets) are decided by the rules below, because Android (ICU) and the JVM disagree on many of
 * them and both miss Portuguese conventions. Sentences in Latin-script text therefore do not depend on the
 * platform; the iterator only adds boundaries after terminators the rules do not know, such as `。` or `।`.
 *
 * A terminator followed by a space ends a sentence unless the next word, after any opening quotes, brackets
 * or dialogue dashes, starts in lowercase, or another terminator follows (`. . .`). So
 * `— Vamos? — perguntou ele.` stays whole, `ele. — Vamos?` starts a new line of dialogue, and an ellipsis
 * before a lowercase word (`pensou... e saiu`) continues the sentence. A lone period does not end a sentence
 * after:
 * - an abbreviation that always precedes a name or a number (`Sr.`, `Dra.`, `Exmo.`, `Prof.`, `Av.`, `pág.`,
 *   `cap.`, `séc.`, `nº`, `Cia.`, ...), when [locale] is Portuguese;
 * - initials (`J. M. Machado de Assis`, `D. Pedro`, `S. Paulo`, `p. 12`), unless a word that usually opens a
 *   Portuguese sentence follows a single letter, as in `D. Pedro I. Ele governou`;
 * - a list marker at the start of the sentence (`1.`, `IV.`).
 *
 * Abbreviations that close a phrase (`etc.`, `Ltda.`, `Jr.`, `a.C.`, `d.C.`) end the sentence exactly when
 * an uppercase word follows them: `peras etc. Depois` splits, `peras etc. para` does not. Decimals and
 * thousands (`3.5`, `1.000`) never split because no space follows their period.
 *
 * A sentence longer than [maxSentenceLength] is cut at the last `;`, else `:`, ` — ` or `,` in the second half
 * of the allowed length, else at the last space, else at the limit.
 *
 * Instances are immutable and safe to share between threads.
 */
public class SentenceSegmenter internal constructor(
    private val locale: Locale,
    /** The longest sentence, in characters, that [split] returns. */
    public val maxSentenceLength: Int,
    private val newBreakIterator: (Locale) -> BreakIterator,
) {
    /**
     * Creates a segmenter for [locale], Brazilian Portuguese by default, that cuts sentences longer than
     * [maxSentenceLength] characters (at least 2).
     */
    public constructor(
        locale: Locale = Locale.forLanguageTag("pt-BR"),
        maxSentenceLength: Int = DEFAULT_MAX_SENTENCE_LENGTH,
    ) : this(locale, maxSentenceLength, BreakIterator::getSentenceInstance)

    init {
        require(maxSentenceLength >= 2) { "maxSentenceLength must be at least 2, was $maxSentenceLength" }
    }

    private val rules = SentenceBoundaryRules(portuguese = locale.language == PORTUGUESE)
    private val longSentences = LongSentenceSplitter(maxSentenceLength)

    /** Splits [text] into sentence ranges, following the rules in the class documentation. */
    public fun split(text: String): List<TextRange> {
        val iterator = newBreakIterator(locale)
        return lines(text).flatMap { line ->
            sentencesIn(text.substring(line.start, line.end), iterator).flatMap { sentence ->
                longSentences.split(text, TextRange(line.start + sentence.start, line.start + sentence.end))
            }
        }
    }

    /** Trimmed, non-empty lines of [text]. */
    private fun lines(text: String): List<TextRange> {
        val lines = mutableListOf<TextRange>()
        var start = 0
        while (start < text.length) {
            val lineBreak = text.indexOfAny(Punctuation.LINE_BREAKS, start).takeIf { it >= 0 } ?: text.length
            text.trimmedRange(start, lineBreak)?.let(lines::add)
            start = lineBreak + 1
        }
        return lines
    }

    /** Sentences of a trimmed, non-empty [line], relative to the line. */
    private fun sentencesIn(
        line: String,
        iterator: BreakIterator,
    ): List<TextRange> {
        val candidates = sortedMapOf<Int, TerminatorCluster?>()
        terminatorCandidates(line).forEach { (next, cluster) -> candidates[next] = cluster }
        foreignCandidates(line, iterator).forEach { candidates.putIfAbsent(it, null) }
        val sentences = mutableListOf<TextRange>()
        var sentenceStart = 0
        for ((next, cluster) in candidates) {
            if (cluster == null || rules.endsSentence(line, sentenceStart, cluster, next)) {
                sentences += TextRange(sentenceStart, line.trimEndIndex(sentenceStart, next))
                sentenceStart = next
            }
        }
        sentences += TextRange(sentenceStart, line.length)
        return sentences
    }

    /** Each place where a sentence could start after a terminator cluster and a space, with that cluster. */
    private fun terminatorCandidates(line: String): List<Pair<Int, TerminatorCluster>> {
        val candidates = mutableListOf<Pair<Int, TerminatorCluster>>()
        var start = line.indexOfAny(Punctuation.TERMINATOR_CHARS)
        while (start >= 0) {
            val cluster = TerminatorCluster.at(line, start)
            val next = line.skipSpaces(cluster.end)
            if (next > cluster.end && next < line.length) candidates += next to cluster
            start = line.indexOfAny(Punctuation.TERMINATOR_CHARS, cluster.end)
        }
        return candidates
    }

    /**
     * Sentence starts that [iterator] finds after marks the rules do not know, such as `。` or `।`. Its other
     * boundaries are dropped: [terminatorCandidates] proposes the ones after known terminators, and the rest
     * are spurious (the JVM's iterator sometimes breaks right before a period, as in `»Ver pág|. '`).
     */
    private fun foreignCandidates(
        line: String,
        iterator: BreakIterator,
    ): List<Int> {
        iterator.setText(line)
        iterator.first()
        return generateSequence { iterator.next().takeIf { it != BreakIterator.DONE } }
            .map { line.skipSpaces(it) }
            .filter { it < line.length && line[it] !in Punctuation.ENDING_MARKS && followsForeignMark(line, it) }
            .toList()
    }

    private fun followsForeignMark(
        line: String,
        index: Int,
    ): Boolean {
        val previous = line.getOrNull(line.trimEndIndex(0, index) - 1) ?: return false
        return previous.code > LAST_ASCII && !previous.isLetterOrDigit() && previous !in Punctuation.LATIN_MARKS
    }

    public companion object {
        /** Default for [maxSentenceLength]: about 20 seconds of narration. */
        public const val DEFAULT_MAX_SENTENCE_LENGTH: Int = 280

        private const val PORTUGUESE = "pt"
        private const val LAST_ASCII = 0x7F
    }
}
