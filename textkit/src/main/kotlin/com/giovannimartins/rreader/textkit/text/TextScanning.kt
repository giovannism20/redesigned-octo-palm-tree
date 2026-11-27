package com.giovannimartins.rreader.textkit.text

import com.giovannimartins.rreader.textkit.model.TextRange

/** Index of the first non-whitespace character in `[from, until)`, or [until] when there is none. */
internal fun CharSequence.skipSpaces(
    from: Int,
    until: Int = length,
): Int {
    var index = from
    while (index < until && this[index].isWhitespace()) index++
    return index
}

/** Exclusive end of `[from, end)` once trailing whitespace is dropped; [from] when it is all whitespace. */
internal fun CharSequence.trimEndIndex(
    from: Int,
    end: Int,
): Int {
    var index = end
    while (index > from && this[index - 1].isWhitespace()) index--
    return index
}

/** `[start, end)` without surrounding whitespace, or null when it is blank. */
internal fun CharSequence.trimmedRange(
    start: Int,
    end: Int,
): TextRange? {
    val trimmedStart = skipSpaces(start, end)
    return if (trimmedStart == end) null else TextRange(trimmedStart, trimEndIndex(trimmedStart, end))
}
