package com.giovannimartins.rreader.textkit.layout

/** A paragraph rebuilt from layout lines. */
internal class Paragraph(
    val text: String,
    /** Index of the paragraph's first line in the document's lines. */
    val firstLine: Int,
    val pageIndex: Int,
    /** The paragraph is a heading (see [ParagraphRules.isHeading]). */
    val isHeading: Boolean,
)

/**
 * Rebuilds the paragraphs of a document from its body lines, in reading order across pages.
 *
 * Paragraph breaks follow [ParagraphRules]. A heading is always a paragraph of its own; consecutive heading lines in
 * the same large font join into one heading. A drop cap joins the word it starts without a space, and lines made only
 * of symbols (`* * *`) end the paragraph and are left out. Lines within a paragraph are joined by [LineJoiner].
 */
internal class ParagraphBuilder(
    private val lines: List<TextLine>,
    metrics: LayoutMetrics,
) {
    private val rules = ParagraphRules(metrics)

    /** The paragraphs, with a paragraph also starting at each line index in [forcedStarts]. */
    fun build(forcedStarts: Set<Int> = emptySet()): List<Paragraph> {
        val collector = Collector()
        var previous: TextLine? = null
        var previousIsHeading = false
        for ((index, line) in lines.withIndex()) {
            if (line.isSeparator) {
                collector.finish()
                previous = null
            } else {
                val step = step(line, lines.getOrNull(index + 1), previous, previousIsHeading, index in forcedStarts)
                if (step.starts) collector.start(index, line, step.isHeading)
                collector.add(previous, line, step.glued)
                previous = line
                previousIsHeading = step.isHeading
            }
        }
        collector.finish()
        return collector.paragraphs
    }

    /** How [line] relates to the [previous] one: whether it starts a paragraph, is a heading, follows a drop cap. */
    private fun step(
        line: TextLine,
        next: TextLine?,
        previous: TextLine?,
        previousIsHeading: Boolean,
        forced: Boolean,
    ): Step {
        if (previous == null || forced) return Step(starts = true, isHeading = rules.isHeading(line, next, true))
        val startsByLayout = rules.startsParagraph(previous, line)
        val isHeading = rules.isHeading(line, next, startsByLayout)
        val afterDropCap = rules.isDropCap(previous, line)
        val starts =
            when {
                afterDropCap -> false
                previousIsHeading && isHeading -> !rules.continuesHeading(previous, line)
                else -> previousIsHeading || isHeading || startsByLayout
            }
        return Step(starts, isHeading, glued = afterDropCap)
    }

    private class Step(
        val starts: Boolean,
        val isHeading: Boolean,
        val glued: Boolean = false,
    )

    private class Collector {
        val paragraphs = mutableListOf<Paragraph>()
        private val text = StringBuilder()
        private var firstLine = 0
        private var pageIndex = 0
        private var isHeading = false

        fun start(
            index: Int,
            line: TextLine,
            heading: Boolean,
        ) {
            finish()
            firstLine = index
            pageIndex = line.pageIndex
            isHeading = heading
        }

        fun add(
            previous: TextLine?,
            line: TextLine,
            glued: Boolean,
        ) {
            if (glued) text.append(line.text) else LineJoiner.append(text, previous, line.text)
        }

        fun finish() {
            if (text.isNotEmpty()) paragraphs += Paragraph(text.toString(), firstLine, pageIndex, isHeading)
            text.setLength(0)
        }
    }
}
