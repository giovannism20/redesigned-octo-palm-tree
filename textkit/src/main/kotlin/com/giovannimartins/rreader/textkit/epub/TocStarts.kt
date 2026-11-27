package com.giovannimartins.rreader.textkit.epub

/** Where a table-of-contents entry points in the spine text. */
internal class TocTarget(
    val paragraph: Int,
    val title: String?,
    /** The entry's depth when it points to an element; null when it points to a whole document. */
    val depth: Int?,
)

/**
 * Turns a table of contents into chapter starts.
 *
 * An entry that points to an element whose `id` the document has starts a chapter at that element's paragraph
 * ([ContentDocument.anchors]); an entry for a whole document, or whose fragment the document lacks, starts one at the
 * document's first paragraph. Entries for documents outside the spine are ignored. Entries for elements only count
 * down to the depth [TocDepth] chooses; entries for whole documents always count. Several entries at the same
 * paragraph make one start, titled by the first of them that has a title.
 */
internal object TocStarts {
    fun resolve(
        entries: List<TocEntry>,
        text: SpineText,
        budget: ParseBudget = ParseBudget.unlimited(),
    ): List<ChapterStart> {
        val targets = entries.mapNotNull { target(it, text) }
        val depth = TocDepth.choose(targets, text, budget)
        return targets
            .filter { (it.depth ?: 0) <= depth }
            .groupBy { it.paragraph }
            .map { (paragraph, same) ->
                ChapterStart(paragraph, same.firstNotNullOfOrNull { it.title }, movable = same.all { it.depth != null })
            }.sortedBy { it.paragraph }
    }

    private fun target(
        entry: TocEntry,
        text: SpineText,
    ): TocTarget? =
        text.documentIndexOf(entry.path)?.let { document ->
            val anchor = entry.fragment?.let { text.anchor(document, it) }
            if (anchor != null) {
                TocTarget(anchor, entry.title, entry.depth)
            } else {
                TocTarget(text.documentStart(document), entry.title, depth = null)
            }
        }
}

/**
 * Chooses how deep in a nested table of contents the entries that point to elements start chapters, so that chapters
 * are the deepest level that still gives sections of a reasonable length.
 *
 * Starting from the top level, the next level down is used when the current one is too coarse, or when the sections
 * the next level adds are at least [MIN_SUBSECTION_LENGTH] characters long in the median. A level is too coarse when
 * a single section holds at least half of the text, or sections longer than [ChapterAssembler.LONG_SECTION_LENGTH]
 * characters do (whole volumes or parts). So chapters nested under parts or under a title-page entry count even when
 * they are short, while short numbered sub-sections inside chapters, title-page lines and closing "FIM" entries
 * nested below chapters do not.
 *
 * Only the first [MAX_LEVELS] levels are considered, so entries nested deeper never start chapters: each level costs
 * a pass over every entry, and a hostile table of contents can nest hundreds of levels.
 */
internal object TocDepth {
    /** Median length the sections of a deeper level need when the level above is not too coarse (3.5 minutes). */
    const val MIN_SUBSECTION_LENGTH: Long = 3_000

    /** The most levels of a nested table of contents considered; books nest parts, chapters and a few sections. */
    const val MAX_LEVELS: Int = 8

    /** The deepest depth whose element entries start chapters. */
    fun choose(
        targets: List<TocTarget>,
        text: SpineText,
        budget: ParseBudget = ParseBudget.unlimited(),
    ): Int {
        val levels = Levels(targets, text)
        val depths =
            targets
                .mapNotNull { it.depth }
                .distinct()
                .sorted()
                .take(MAX_LEVELS)
        val deeper =
            depths.zipWithNext().takeWhile { (current, next) ->
                budget.check()
                levels.refines(current, next)
            }
        return deeper.lastOrNull()?.second ?: depths.firstOrNull() ?: 0
    }

    /** The sections each depth of a table of contents would give. */
    private class Levels(
        targets: List<TocTarget>,
        private val text: SpineText,
    ) {
        private val documentStarts = targets.filter { it.depth == null }.map { it.paragraph }

        /** The paragraph and depth of each entry that points to an element. */
        private val elementStarts = targets.mapNotNull { target -> target.depth?.let { target.paragraph to it } }

        /** Whether chapters should start at the entries of depth [next] too, and not only down to [current]. */
        fun refines(
            current: Int,
            next: Int,
        ): Boolean {
            val shallow = bounds(current)
            val deep = bounds(next)
            val added = deep - shallow.toSet()
            return added.isEmpty() || isCoarse(shallow) || medianLength(added, deep) >= MIN_SUBSECTION_LENGTH
        }

        /** The paragraphs where sections start down to [depth], the leading one included, then the end of the text. */
        private fun bounds(depth: Int): List<Int> {
            val starts = elementStarts.filter { it.second <= depth }.map { it.first }
            return (listOf(0) + documentStarts + starts + text.size).filter { it <= text.size }.distinct().sorted()
        }

        /** Whether one section, or the long sections together, hold at least half of the text. */
        private fun isCoarse(bounds: List<Int>): Boolean {
            val lengths = bounds.zipWithNext { from, to -> text.length(from, to) }
            val long = lengths.filter { it > ChapterAssembler.LONG_SECTION_LENGTH }.sum()
            val half = (lengths.sum() + 1) / 2
            return long >= half || (lengths.maxOrNull() ?: 0) >= half
        }

        /** The median length of the sections that start at [starts], given every section start in [bounds]. */
        private fun medianLength(
            starts: List<Int>,
            bounds: List<Int>,
        ): Long {
            val lengths = starts.map { start -> text.length(start, bounds[bounds.binarySearch(start) + 1]) }.sorted()
            return lengths[lengths.size / 2]
        }
    }
}
