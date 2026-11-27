package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertWithMessage

/**
 * Asserts the contract shared by sentences and segments: trimmed, non-empty, ordered, non-overlapping ranges
 * of at most [maxLength] characters that never cross a paragraph and together cover every non-whitespace
 * character of [text].
 */
internal fun assertWellFormed(
    text: String,
    ranges: List<TextRange>,
    maxLength: Int,
) {
    var previousEnd = 0
    for (range in ranges) {
        val context = assertWithMessage("range %s of %s in <%s>", range, ranges, text)
        context.that(range.start).isAtLeast(previousEnd)
        context.that(range.length).isAtLeast(1)
        context.that(range.length).isAtMost(maxLength)
        context.that(text[range.start].isWhitespace()).isFalse()
        context.that(text[range.end - 1].isWhitespace()).isFalse()
        context.that(text.substring(previousEnd, range.start).isBlank()).isTrue()
        context.that(text.substring(range.start, range.end)).doesNotContain(Chapter.PARAGRAPH_SEPARATOR)
        previousEnd = range.end
    }
    assertWithMessage("text after the last range of %s in <%s>", ranges, text)
        .that(text.substring(previousEnd).isBlank())
        .isTrue()
}

/** The text of each range. */
internal fun String.slices(ranges: List<TextRange>): List<String> = ranges.map { substring(it.start, it.end) }
