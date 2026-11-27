package com.giovannimartins.rreader.textkit.epub

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Safety limits for reading an EPUB (security requirement SR-2). They stop zip bombs and archives with huge numbers
 * of entries before they exhaust memory, and books built to take forever to read.
 *
 * Byte limits count decompressed bytes as they are actually read, never the sizes an archive declares: a hostile
 * archive can declare tiny sizes for entries that inflate to gigabytes. An entry that declares more than
 * [maxEntryBytes] is rejected without being read.
 */
public data class ArchiveLimits(
    /** Maximum number of entries the archive may contain. */
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    /** Maximum decompressed size of any single entry that is read. */
    val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    /** Maximum decompressed bytes read from all entries during one parse. */
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    /**
     * How long one parse may run. It is checked before every spine document, for every element of a content document
     * or table of contents and in the loops that assemble chapters; a parse past it fails with
     * [EpubError.TooComplex].
     */
    val maxParseTime: Duration = DEFAULT_MAX_PARSE_TIME,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxEntryBytes > 0) { "maxEntryBytes must be positive" }
        require(maxTotalBytes > 0) { "maxTotalBytes must be positive" }
        require(maxParseTime.isPositive()) { "maxParseTime must be positive" }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 10_000
        const val DEFAULT_MAX_ENTRY_BYTES = 32L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES = 256L * 1024 * 1024
        val DEFAULT_MAX_PARSE_TIME = 2.minutes
    }
}

/** Names the [ArchiveLimits] value an archive exceeded; reported by [EpubError.LimitExceeded]. */
public enum class ArchiveLimit {
    /** [ArchiveLimits.maxEntries]. */
    MAX_ENTRIES,

    /** [ArchiveLimits.maxEntryBytes]. */
    MAX_ENTRY_BYTES,

    /** [ArchiveLimits.maxTotalBytes]. */
    MAX_TOTAL_BYTES,
}
