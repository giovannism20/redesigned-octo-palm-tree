package com.giovannimartins.rreader.textkit.layout

/**
 * Where paragraphs and headings start, judged from consecutive lines (see [ParagraphBuilder]).
 *
 * A line starts a new paragraph after the previous one, unless it continues a word broken across the lines, when:
 * - it starts with a dialogue dash and the previous line ends a sentence;
 * - there is a clear gap above it, or its font size changes ([LineShape.hasGapBefore], [LineShape.changesFontSize]);
 * - it is indented, centered lines included, or the previous line is centered ([LineShape.isIndented],
 *   [LineShape.isCentered]);
 * - the previous line ends a sentence and stops clearly short of the column's right edge ([LineShape.isShort]).
 *
 * A sentence ends with `.`, `!`, `?`, `…`, `:` or `;`, possibly followed by closing quotes or brackets.
 */
internal class ParagraphRules(
    metrics: LayoutMetrics,
) {
    private val shape = LineShape(metrics)

    fun startsParagraph(
        previous: TextLine,
        line: TextLine,
    ): Boolean =
        when {
            continuesWord(previous, line) -> false
            startsDialogue(line) && endsSentence(previous.text) -> true
            shape.hasGapBefore(previous, line) || shape.changesFontSize(previous, line) -> true
            shape.isIndented(line) || shape.isCentered(previous) -> true
            else -> endsSentence(previous.text) && shape.isShort(previous)
        }

    /** A big initial letter on a line of its own, whose word continues on [next]. */
    fun isDropCap(
        line: TextLine,
        next: TextLine?,
    ): Boolean = line.text.length == 1 && line.text[0].isLetter() && shape.isLargeFont(line) && startsLowerCase(next)

    /**
     * A heading: a line of at most [Headings.MAX_LENGTH] characters that is set in a large font (and is not a drop
     * cap), or that is worded like a heading ([Headings]) and starts a paragraph ([startsByLayout]).
     */
    fun isHeading(
        line: TextLine,
        next: TextLine?,
        startsByLayout: Boolean,
    ): Boolean =
        when {
            line.text.length > Headings.MAX_LENGTH -> false
            shape.isLargeFont(line) -> !isDropCap(line, next)
            else -> startsByLayout && Headings.isNamedHeading(line.text)
        }

    /** Two heading lines in the same large font without a gap are one heading broken across lines. */
    fun continuesHeading(
        previous: TextLine,
        line: TextLine,
    ): Boolean =
        shape.isLargeFont(previous) &&
            shape.isLargeFont(line) &&
            shape.sizeRatio(previous, line) <= SAME_HEADING_RATIO &&
            !shape.hasGapBefore(previous, line)

    private fun continuesWord(
        previous: TextLine,
        line: TextLine,
    ): Boolean =
        previous.softHyphen ||
            (
                Hyphens.endsWord(previous.text) &&
                    previous.text[previous.text.length - 2].isLetter() &&
                    (startsLowerCase(line) || repeatsHyphen(line))
            )

    private fun repeatsHyphen(line: TextLine): Boolean =
        line.text.first() in Hyphens.CHARACTERS && line.text.getOrNull(1)?.isLetter() == true

    private fun startsLowerCase(line: TextLine?): Boolean = line?.text?.first()?.isLowerCase() == true

    private fun startsDialogue(line: TextLine): Boolean = line.text.first() in DIALOGUE_DASHES

    companion object {
        private const val SAME_HEADING_RATIO = 1.1f
        private const val DIALOGUE_DASHES = "—–\u2015"
        private const val SENTENCE_ENDS = ".!?…:;"
        private const val CLOSING_MARKS = "\"'”’»)]"

        /** Whether [text] ends a sentence, closing quotes and brackets aside. */
        fun endsSentence(text: String): Boolean =
            text.trimEnd { it in CLOSING_MARKS || it.isWhitespace() }.lastOrNull()?.let { it in SENTENCE_ENDS } == true
    }
}
