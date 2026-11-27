package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter

/** A chapter and whether its first paragraph opens a spine document. */
internal class AssembledChapter(
    val chapter: Chapter,
    val opensDocument: Boolean,
) {
    val isShort: Boolean get() = chapter.text.length < ShortChapters.MAX_SHORT_LENGTH
}

/**
 * Joins chapters too short to stand alone, such as title-page lines, part titles and a closing "FIM" that the table of
 * contents lists, to a neighbour in the same spine document.
 *
 * A chapter shorter than [MAX_SHORT_LENGTH] characters joins the chapter after it when that one starts in the same
 * document; joined chapters that are still short go on joining. A short chapter that cannot join the next one joins
 * the chapter before it, unless it opens a document. A joined chapter keeps the title of the part that is not short,
 * or of the first part when both are. Chapters never join across documents, so a book made of one file per chapter
 * keeps every chapter, however short.
 */
internal object ShortChapters {
    /** Chapters with fewer characters than this (about twenty seconds of listening) join a neighbour. */
    const val MAX_SHORT_LENGTH: Int = 300

    fun merge(chapters: List<AssembledChapter>): List<Chapter> {
        val forward = join(chapters) { previous, next -> previous.isShort && !next.opensDocument }
        return join(forward) { previous, next -> next.isShort && !next.opensDocument }.map { it.chapter }
    }

    /** Joins each chapter to the one before it when [joins] says so. */
    private fun join(
        chapters: List<AssembledChapter>,
        joins: (previous: AssembledChapter, next: AssembledChapter) -> Boolean,
    ): List<AssembledChapter> {
        val joined = mutableListOf<AssembledChapter>()
        for (chapter in chapters) {
            val previous = joined.lastOrNull()
            if (previous != null && joins(previous, chapter)) {
                joined[joined.lastIndex] = previous + chapter
            } else {
                joined += chapter
            }
        }
        return joined
    }

    /** The two chapters as one, titled by the one that is not short (the first one when both are), else the other. */
    private operator fun AssembledChapter.plus(next: AssembledChapter): AssembledChapter {
        val nextTitled = isShort && !next.isShort
        val title = if (nextTitled) next.chapter.title ?: chapter.title else chapter.title ?: next.chapter.title
        val text = chapter.text + Chapter.PARAGRAPH_SEPARATOR + next.chapter.text
        return AssembledChapter(Chapter(title, text), opensDocument)
    }
}
