package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter

/** The extracted text of one spine document. */
internal class SpineDocument(
    val path: String,
    val content: ContentDocument,
)

/** Groups spine documents into chapters. */
internal object ChapterAssembler {
    /**
     * Uses the first of [tablesOfContents] that points to at least one of the [documents]. A document it points to
     * starts a chapter titled by the first titled entry for that document; the documents after it join that chapter
     * until the next document it points to, because books often split one chapter across several files. Documents
     * before the first one it points to form a leading chapter. When no table of contents fits, every document is a
     * chapter of its own. A chapter without a title from the table of contents takes the first `h1`–`h3` heading of
     * its documents, and chapters without readable text are dropped.
     */
    fun assemble(
        documents: List<SpineDocument>,
        tablesOfContents: Sequence<List<TocEntry>>,
    ): List<Chapter> {
        val paths = documents.mapTo(HashSet()) { it.path }
        val titles = tablesOfContents.map(::titlesByPath).firstOrNull { it.keys.any(paths::contains) }.orEmpty()
        val groups = mutableListOf<ChapterGroup>()
        for (document in documents) {
            val startsChapter = groups.isEmpty() || titles.isEmpty() || document.path in titles
            if (startsChapter) groups += ChapterGroup(titles[document.path])
            groups.last().contents += document.content
        }
        return groups.mapNotNull { it.toChapter() }
    }

    private fun titlesByPath(entries: List<TocEntry>): Map<String, String?> =
        entries.groupBy { it.path }.mapValues { (_, sameTarget) -> sameTarget.firstNotNullOfOrNull { it.title } }

    private class ChapterGroup(
        private val tocTitle: String?,
    ) {
        val contents = mutableListOf<ContentDocument>()

        fun toChapter(): Chapter? {
            val paragraphs = contents.flatMap { it.paragraphs }
            val title = tocTitle ?: contents.firstNotNullOfOrNull { it.heading }
            return if (paragraphs.isEmpty()) null else Chapter.fromParagraphs(title, paragraphs)
        }
    }
}
