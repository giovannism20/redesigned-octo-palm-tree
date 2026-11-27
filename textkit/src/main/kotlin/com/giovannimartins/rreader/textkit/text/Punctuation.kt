package com.giovannimartins.rreader.textkit.text

/** Punctuation classes shared by the sentence rules. */
internal object Punctuation {
    /** Marks that can end a sentence. */
    const val TERMINATORS: String = ".!?…‼⁇⁈⁉"

    /** Closing quotes and brackets that stay with the sentence they follow, as in `?»` or `.)`. */
    const val CLOSERS: String = "\"'»”’)]}›"

    /**
     * Closers that never open a quotation in Portuguese text, so they still belong to the previous sentence
     * when a space separates them from the terminator, as in `embora. »`.
     */
    const val SPACED_CLOSERS: String = "»”)]}›"

    /** Marks that may come before the first word of a sentence: quotes, brackets, dialogue dashes. */
    const val OPENERS: String = "\"'«“‘„‹([{¿¡—–―-"

    /** Marks that close a sentence and so never start one: [TERMINATORS] and [CLOSERS]. */
    const val ENDING_MARKS: String = TERMINATORS + CLOSERS

    /** Every mark the sentence rules know about. */
    const val LATIN_MARKS: String = ENDING_MARKS + OPENERS

    /** Line terminators. A line break always ends a sentence. */
    val LINE_BREAKS: CharArray = charArrayOf('\n', '\r', '\u000B', '\u000C', '\u0085', ' ', ' ')

    /** [TERMINATORS] as an array, for [String.indexOfAny]. */
    val TERMINATOR_CHARS: CharArray = TERMINATORS.toCharArray()
}
