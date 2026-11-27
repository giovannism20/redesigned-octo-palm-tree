package com.giovannimartins.rreader.textkit.model

/**
 * A book whose text has been cleaned for reading and listening. It is produced
 * once at import time and never changes afterwards, so [ReadingPosition]s stay
 * valid (see ADR 0006).
 */
public data class NormalizedBook(
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val cover: CoverImage? = null,
) {
    init {
        require(chapters.isNotEmpty()) { "A book needs at least one chapter" }
    }

    /** Total number of characters across all chapter texts. */
    val totalLength: Long get() = chapters.sumOf { it.text.length.toLong() }
}

/** Descriptive metadata. Fields are blank or null when the source has none. */
public data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    /** BCP 47 language tag from the source, for example `pt-BR`, or null when unknown. */
    val language: String? = null,
    val description: String? = null,
)

/**
 * One chapter of a [NormalizedBook].
 *
 * [text] is the displayable chapter text: paragraphs separated by
 * [PARAGRAPH_SEPARATOR], whitespace collapsed, no other line breaks. Character
 * offsets in [ReadingPosition] index into it. Speech-only rewrites (for example
 * Roman numerals) happen later, when audio is rendered, and never change it.
 */
public data class Chapter(
    /** Title from the table of contents or a heading, or null when the source has none. */
    val title: String?,
    val text: String,
) {
    init {
        require(text.isNotBlank()) { "A chapter needs readable text" }
    }

    /** Ranges of each paragraph within [text]. */
    public fun paragraphRanges(): List<TextRange> = paragraphRangesOf(text)

    public companion object {
        /** Separator between paragraphs inside [text]. */
        public const val PARAGRAPH_SEPARATOR: String = "\n\n"

        /**
         * Ranges of the non-empty paragraphs of [chapterText], a chapter's normalized text: the text between
         * [PARAGRAPH_SEPARATOR]s, in order. Character offsets are the chapter's own, so a
         * [ReadingPosition] or a segment maps to its paragraph without copying the text.
         */
        public fun paragraphRangesOf(chapterText: String): List<TextRange> {
            val ranges = mutableListOf<TextRange>()
            var start = 0
            while (start <= chapterText.length) {
                val separator = chapterText.indexOf(PARAGRAPH_SEPARATOR, start)
                val end = if (separator < 0) chapterText.length else separator
                if (end > start) ranges += TextRange(start, end)
                if (separator < 0) break
                start = separator + PARAGRAPH_SEPARATOR.length
            }
            return ranges
        }

        /** Builds a chapter from paragraphs, dropping blank ones. */
        public fun fromParagraphs(
            title: String?,
            paragraphs: List<String>,
        ): Chapter =
            Chapter(
                title = title,
                text = paragraphs.map(String::trim).filter(String::isNotEmpty).joinToString(PARAGRAPH_SEPARATOR),
            )
    }
}

/** A cover picture as stored in the source file. */
public class CoverImage(
    /** IANA media type, for example `image/jpeg`. */
    public val mediaType: String,
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is CoverImage && mediaType == other.mediaType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * mediaType.hashCode() + bytes.contentHashCode()

    override fun toString(): String = "CoverImage(mediaType=$mediaType, size=${bytes.size})"
}
