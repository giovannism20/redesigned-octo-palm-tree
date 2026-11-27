package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.TextCleaner

/** A layout line whose text went through [TextCleaner]; never blank. */
internal class TextLine(
    val text: String,
    val box: LayoutLine,
    val pageIndex: Int,
    /** The line ended with a soft hyphen, which the cleaner removed: its last word continues on the next line. */
    val softHyphen: Boolean,
) {
    /** Lines made only of symbols, such as `* * *`, separate scenes and are not read aloud. */
    val isSeparator: Boolean = text.none(Char::isLetterOrDigit)

    override fun toString(): String = "TextLine(page=$pageIndex, text=$text)"
}

/** A page whose lines are [TextLine]s. */
internal class TextPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val lines: List<TextLine>,
) {
    fun withLines(kept: List<TextLine>): TextPage = TextPage(index, width, height, kept)
}

private const val SOFT_HYPHEN = '\u00AD'

/** Cleans the text of every line and drops the lines left empty. */
internal fun PageLayout.toTextPage(): TextPage =
    TextPage(
        index = index,
        width = width,
        height = height,
        lines =
            lines.mapNotNull { line ->
                val text = TextCleaner.clean(line.text)
                if (text.isEmpty()) null else TextLine(text, line, index, line.text.trimEnd().endsWith(SOFT_HYPHEN))
            },
    )
