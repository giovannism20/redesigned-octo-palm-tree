package com.giovannimartins.rreader.textkit.model

/** A half-open range `[start, end)` of character offsets within a chapter text. */
public data class TextRange(
    val start: Int,
    val end: Int,
) {
    init {
        require(start in 0..end) { "Invalid range [$start, $end)" }
    }

    /** Number of characters covered. */
    val length: Int get() = end - start

    /** Whether [offset] falls inside this range. */
    public operator fun contains(offset: Int): Boolean = offset in start until end
}
