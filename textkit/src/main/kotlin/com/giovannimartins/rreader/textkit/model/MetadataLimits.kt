package com.giovannimartins.rreader.textkit.model

/**
 * Bounds on the descriptive text that books and catalogue entries carry: titles (of books, chapters and catalogue
 * entries), author names, language tags and descriptions. They come from untrusted files and feeds, and the app stores,
 * shows and hands them around whole, so a single unbounded value can exhaust memory or break storage: a database row
 * larger than a cursor window (2 MB) cannot be read back at all.
 *
 * Every producer of such values in this library applies these bounds ([BookMetadata] of EPUBs and PDFs, chapter
 * titles, catalogue feeds), and the app applies them to the values it makes up itself, such as a title taken from a
 * file name. Values over a bound are cut, never rejected, so a book with absurd metadata still opens.
 */
public object MetadataLimits {
    /** The longest title of a book, a chapter or a catalogue entry, in characters. */
    public const val MAX_TITLE_LENGTH: Int = 300

    /** The most authors kept for one book. */
    public const val MAX_AUTHORS: Int = 20

    /** The longest author name, in characters. */
    public const val MAX_AUTHOR_LENGTH: Int = 300

    /** The longest language tag kept; real BCP 47 tags such as `pt-BR` are far shorter. */
    public const val MAX_LANGUAGE_LENGTH: Int = 35

    /** The longest description, in characters (about 4 KB of text). */
    public const val MAX_DESCRIPTION_LENGTH: Int = 4_000

    private const val ELLIPSIS = "…"

    /** A primary language subtag of two or three letters, then subtags of up to eight letters or digits. */
    private val LANGUAGE_TAG = Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{1,8})*")

    /** [text], a title, cut to at most [MAX_TITLE_LENGTH] characters. */
    public fun title(text: String): String = cut(text, MAX_TITLE_LENGTH)

    /** [text], an author name, cut to at most [MAX_AUTHOR_LENGTH] characters; keep at most [MAX_AUTHORS] of them. */
    public fun author(text: String): String = cut(text, MAX_AUTHOR_LENGTH)

    /**
     * [tag] when it reads like a BCP 47 language tag (`pt`, `pt-BR`, `por`) of at most [MAX_LANGUAGE_LENGTH]
     * characters, else null.
     */
    public fun language(tag: String): String? =
        tag.takeIf {
            it.length <= MAX_LANGUAGE_LENGTH &&
                LANGUAGE_TAG.matches(it)
        }

    /** [text], a description, cut to at most [MAX_DESCRIPTION_LENGTH] characters. */
    public fun description(text: String): String = cut(text, MAX_DESCRIPTION_LENGTH)

    /**
     * [text] when it has at most [length] characters; otherwise its start followed by an ellipsis, [length] characters
     * at most in all. A character outside the Basic Multilingual Plane is never split in half.
     */
    private fun cut(
        text: String,
        length: Int,
    ): String {
        if (text.length <= length) return text
        var end = length - ELLIPSIS.length
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }
}
