package com.giovannimartins.rreader.textkit.epub

/** The extracted text of one spine document. */
internal class SpineDocument(
    val path: String,
    val content: ContentDocument,
)

/**
 * The paragraphs of the spine documents as one sequence in reading order, with what chapter detection needs to know
 * about them: where each document starts, where its anchors point, which paragraphs are headings and where chapter
 * divisions open. Paragraph indices are positions in this sequence, so a chapter may start in one document and end
 * in another.
 */
internal class SpineText(
    private val documents: List<SpineDocument>,
) {
    /** Every paragraph, in reading order. */
    val paragraphs: List<String> = documents.flatMap { it.content.paragraphs }

    /** The index of each document's first paragraph, then the paragraph count. */
    private val documentStarts: IntArray =
        documents.runningFold(0) { start, document -> start + document.content.paragraphs.size }.toIntArray()

    /** The headings, with indices into [paragraphs]. */
    val headings: List<HeadingBlock> =
        documents.flatMapIndexed { index, document ->
            val start = documentStarts[index]
            document.content.headings.map {
                HeadingBlock(it.level, start + it.firstParagraph, start + it.endParagraph, it.text)
            }
        }

    /** The first paragraph of each chapter division, as indices into [paragraphs]. */
    val chapterDivisions: List<Int> =
        documents.flatMapIndexed { index, document ->
            document.content.chapterDivisions.map { documentStarts[index] + it }
        }

    private val documentIndices: Map<String, Int> = documents.withIndex().associate { it.value.path to it.index }

    /** The number of characters before each paragraph, then the total. */
    private val offsets: LongArray = paragraphs.runningFold(0L) { total, text -> total + text.length }.toLongArray()

    /** Whether each paragraph is (part of) a heading. */
    private val headingParagraphs =
        BooleanArray(paragraphs.size).also { flags ->
            headings.forEach { flags.fill(true, it.firstParagraph, it.endParagraph) }
        }

    /** The number of paragraphs. */
    val size: Int get() = paragraphs.size

    /** The index of the spine document stored as [path], or null when the spine has none. */
    fun documentIndexOf(path: String): Int? = documentIndices[path]

    /** The paragraph that starts document [document]; for a document without text, the paragraph after it. */
    fun documentStart(document: Int): Int = documentStarts[document]

    /** The paragraph that the anchor named [id] in document [document] points to, or null when it has none. */
    fun anchor(
        document: Int,
        id: String,
    ): Int? = documents[document].content.anchors[id]?.let { documentStarts[document] + it }

    /** Whether [paragraph] is the first paragraph of a spine document. */
    fun opensDocument(paragraph: Int): Boolean = documentStarts.binarySearch(paragraph) >= 0

    /** Whether [paragraph] is (part of) a heading. */
    fun isHeading(paragraph: Int): Boolean = headingParagraphs[paragraph]

    /** The number of characters from paragraph [from] up to, not including, paragraph [to]. */
    fun length(
        from: Int,
        to: Int,
    ): Long = offsets[to] - offsets[from]

    /** The headings that start from paragraph [from] up to, not including, paragraph [to]. */
    fun headingsIn(
        from: Int,
        to: Int,
    ): List<HeadingBlock> = headings.between(from, to) { it.firstParagraph }

    /** The chapter divisions that start from paragraph [from] up to, not including, paragraph [to]. */
    fun chapterDivisionsIn(
        from: Int,
        to: Int,
    ): List<Int> = chapterDivisions.between(from, to) { it }

    /** The text of the first `h1`–`h3` heading from paragraph [from] up to, not including, [to], or null. */
    fun titleIn(
        from: Int,
        to: Int,
    ): String? = headingsIn(from, to).firstOrNull { it.isTitleLevel }?.text
}

/** The elements of this list, sorted by [paragraph], whose paragraph is from [from] up to, not including, [to]. */
private fun <T> List<T>.between(
    from: Int,
    to: Int,
    paragraph: (T) -> Int,
): List<T> {
    fun firstFrom(index: Int): Int {
        val found = binarySearch { paragraph(it).compareTo(index) }
        return if (found >= 0) found else -found - 1
    }
    return subList(firstFrom(from), firstFrom(to))
}
