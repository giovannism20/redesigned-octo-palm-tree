package com.giovannimartins.rreader.textkit.epub

import java.io.ByteArrayOutputStream

/**
 * Turns references found inside an EPUB (hrefs, `full-path`, `src`) into archive entry names. The names are only used
 * to look entries up in the archive (SR-1); a reference that would leave the archive root resolves to null and is
 * treated like a missing entry.
 */
internal object ArchivePaths {
    private val URL_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private const val PERCENT = '%'.code
    private const val HEX_RADIX = 16
    private const val ESCAPE_LENGTH = 3

    /** The directory that contains the entry [path], without a trailing slash; empty at the archive root. */
    fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    /**
     * Resolves [href], a URL reference found in a document stored in [baseDirectory], to an entry name. The fragment
     * and query are dropped, percent-escapes are decoded and `.`/`..` segments are applied. Returns null for absolute
     * URLs (`http:`, `file:` and the like never point into the archive), for same-document references and for
     * references that climb above the archive root.
     */
    fun resolve(
        baseDirectory: String,
        href: String,
    ): String? {
        val reference = href.substringBefore('#').substringBefore('?').trim()
        if (reference.isEmpty() || URL_SCHEME.containsMatchIn(reference)) return null
        val path = percentDecode(reference)
        val base = if (path.startsWith('/')) emptyList() else baseDirectory.split('/')
        return normalize(base + path.split('/'))
    }

    /** The fragment of [href] (what follows `#`), percent-decoded; null when there is none or it is empty. */
    fun fragmentOf(href: String): String? =
        href
            .substringAfter('#', missingDelimiterValue = "")
            .trim()
            .ifEmpty { null }
            ?.let(::percentDecode)

    private fun normalize(segments: List<String>): String? {
        val kept = ArrayDeque<String>()
        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> kept.removeLastOrNull() ?: return null
                else -> kept.addLast(segment)
            }
        }
        return kept.joinToString("/").ifEmpty { null }
    }

    /** Decodes `%XX` escapes as UTF-8. Malformed escapes stay as they are and `+` stays a plus sign. */
    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = value.toByteArray(Charsets.UTF_8)
        val decoded = ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val escaped = if (bytes[index].toInt() == PERCENT) hexByteAt(bytes, index + 1) else null
            if (escaped != null) {
                decoded.write(escaped)
                index += ESCAPE_LENGTH
            } else {
                decoded.write(bytes[index].toInt())
                index++
            }
        }
        return String(decoded.toByteArray(), Charsets.UTF_8)
    }

    private fun hexByteAt(
        bytes: ByteArray,
        index: Int,
    ): Int? {
        val high = bytes.getOrNull(index)?.let { Character.digit(it.toInt(), HEX_RADIX) } ?: -1
        val low = bytes.getOrNull(index + 1)?.let { Character.digit(it.toInt(), HEX_RADIX) } ?: -1
        return if (high < 0 || low < 0) null else high * HEX_RADIX + low
    }
}
