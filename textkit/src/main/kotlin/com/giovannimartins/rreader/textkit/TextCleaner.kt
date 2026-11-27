package com.giovannimartins.rreader.textkit

import java.text.Normalizer

/** Turns raw document text into the normalized form stored in chapters, for every source format. */
internal object TextCleaner {
    /** Soft hyphen and zero-width characters: invisible in print, and noise for a voice. */
    private val INVISIBLE = setOf('\u00AD', '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

    /**
     * Collapses every run of whitespace (line breaks, tabs, no-break and other Unicode spaces) into one space, trims
     * the ends, removes soft hyphens, zero-width and control characters, and composes accents (Unicode NFC), so that
     * "ção" is stored the same way whatever the source used. Punctuation and dialogue dashes are kept as they are.
     */
    fun clean(raw: CharSequence): String {
        val text = StringBuilder(raw.length)
        var pendingSpace = false
        for (char in raw) {
            if (char.isWhitespace()) {
                pendingSpace = text.isNotEmpty()
            } else if (char !in INVISIBLE && !char.isISOControl()) {
                if (pendingSpace) text.append(' ')
                pendingSpace = false
                text.append(char)
            }
        }
        return Normalizer.normalize(text, Normalizer.Form.NFC)
    }
}
