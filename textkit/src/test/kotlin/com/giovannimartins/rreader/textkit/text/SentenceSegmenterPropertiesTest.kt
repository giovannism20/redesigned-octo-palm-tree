package com.giovannimartins.rreader.textkit.text

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.text.BreakIterator
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Locale
import kotlin.random.Random

class SentenceSegmenterPropertiesTest {
    @Test
    fun `keeps the range contract for arbitrary text`() {
        val segmenters =
            listOf(
                SentenceSegmenter(),
                SentenceSegmenter(Locale.ENGLISH),
                SentenceSegmenter(maxSentenceLength = 30),
                SentenceSegmenter(maxSentenceLength = 2),
            )

        for (text in PortugueseSamples.TEXTS + randomTexts(seed = 1, count = 2_000, tokens = ALL_TOKENS)) {
            for (segmenter in segmenters) {
                assertWellFormed(text, segmenter.split(text), segmenter.maxSentenceLength)
            }
        }
    }

    @Test
    fun `gives the same sentences whatever the break iterator proposes after known terminators`() {
        // ICU on Android and the JVM's own iterator disagree on many of these boundaries; the rules must not.
        val reference = SentenceSegmenter()
        val alternatives =
            listOf(
                segmenterWith { emptyList() },
                segmenterWith { text -> text.indices.filter { text[it] in Punctuation.TERMINATORS }.map { it + 1 } },
                segmenterWith { text ->
                    text.indices.filter { text[it] in Punctuation.TERMINATORS }.map { text.skipSpaces(it + 1) }
                },
            )

        for (text in PortugueseSamples.TEXTS + randomTexts(seed = 2, count = 2_000, tokens = LATIN_TOKENS)) {
            val expected = reference.split(text)
            for (alternative in alternatives) {
                assertWithMessage("<%s>", text).that(alternative.split(text)).isEqualTo(expected)
            }
        }
    }

    private fun segmenterWith(boundariesOf: (String) -> List<Int>): SentenceSegmenter =
        SentenceSegmenter(Locale.forLanguageTag("pt-BR"), SentenceSegmenter.DEFAULT_MAX_SENTENCE_LENGTH) {
            ScriptedBreakIterator(boundariesOf)
        }

    private fun randomTexts(
        seed: Int,
        count: Int,
        tokens: List<String>,
    ): List<String> {
        val random = Random(seed)
        return List(count) {
            buildString {
                repeat(random.nextInt(0, 160)) {
                    append(tokens.random(random))
                    if (random.nextInt(3) > 0) append(' ')
                }
            }
        }
    }

    /** A sentence iterator that proposes the boundaries [boundariesOf] picks, plus both ends of the text. */
    private class ScriptedBreakIterator(
        private val boundariesOf: (String) -> List<Int>,
    ) : BreakIterator() {
        private var source: CharacterIterator = StringCharacterIterator("")
        private var boundaries = intArrayOf(0)
        private var position = 0

        override fun setText(newText: CharacterIterator) {
            source = newText
            val text =
                buildString {
                    var char = newText.first()
                    while (char != CharacterIterator.DONE) {
                        append(char)
                        char = newText.next()
                    }
                }
            boundaries = (boundariesOf(text) + 0 + text.length).distinct().sorted().toIntArray()
            position = 0
        }

        override fun getText(): CharacterIterator = source

        override fun first(): Int {
            position = 0
            return boundaries[position]
        }

        override fun last(): Int {
            position = boundaries.lastIndex
            return boundaries[position]
        }

        override fun next(): Int = if (position < boundaries.lastIndex) boundaries[++position] else BreakIterator.DONE

        override fun next(n: Int): Int {
            position = (position + n).coerceIn(0, boundaries.lastIndex)
            return boundaries[position]
        }

        override fun previous(): Int = if (position > 0) boundaries[--position] else BreakIterator.DONE

        override fun following(offset: Int): Int {
            position = boundaries.indexOfFirst { it > offset }.takeIf { it >= 0 } ?: boundaries.lastIndex
            return boundaries[position]
        }

        override fun current(): Int = boundaries[position]
    }

    private companion object {
        /** Words, abbreviations, numbers, punctuation and spacing, separated by `|`. */
        val LATIN_TOKENS =
            (
                "Era|uma|vez|ele|Ela|disse|casa|É|fazê-lo|Peguei-o|Depois|" +
                    "Sr.|Dra.|etc.|Ltda.|a.C.|J.|M.|p.|pág.|IV.|1.|3.5|1.000|R$|10,50|" +
                    ".|.|!|?|...|…|?!|!!!|,|;|:|—|–|-|«|»|“|”|\"|'|(|)|" +
                    " | |  | |\n|\n\n|\r\n"
            ).split('|')

        val ALL_TOKENS = LATIN_TOKENS + "今日は晴れです。|है।|😀|é|palavramuitolongasemespaçonenhumparacortar".split('|')
    }
}
