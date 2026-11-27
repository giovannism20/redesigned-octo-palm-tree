package com.giovannimartins.rreader.textkit

/** Book formats R Reader can import. */
public enum class BookFormat {
    EPUB,
    PDF,
}

/** Outcome of inspecting the first bytes of a file with [BookFormatDetector]. */
public sealed interface FormatDetection {
    /** The content signature identifies [format] with confidence. */
    public data class Detected(
        val format: BookFormat,
    ) : FormatDetection

    /**
     * A ZIP archive without the EPUB `mimetype` marker as its first entry.
     * Some non-conforming EPUBs look like this, so the EPUB parser must decide.
     */
    public data object ZipArchive : FormatDetection

    /** Neither a PDF nor a ZIP archive. */
    public data object Unsupported : FormatDetection
}

/**
 * Detects a book's format from its content, never from its file name, because
 * files shared from other apps often arrive without a meaningful name or with a
 * generic MIME type.
 *
 * Callers pass up to [HEADER_SIZE] bytes from the start of the file.
 */
public object BookFormatDetector {
    /** Number of leading bytes [detect] looks at. */
    public const val HEADER_SIZE: Int = 1024

    private val PDF_MARKER = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val EPUB_MIMETYPE_NAME = "mimetype".toByteArray(Charsets.US_ASCII)
    private val EPUB_MIMETYPE_VALUE = "application/epub+zip".toByteArray(Charsets.US_ASCII)

    // ZIP local file header layout (APPNOTE.TXT, section 4.3.7).
    private const val COMPRESSION_METHOD_OFFSET = 8
    private const val COMPRESSED_SIZE_OFFSET = 18
    private const val NAME_LENGTH_OFFSET = 26
    private const val EXTRA_LENGTH_OFFSET = 28
    private const val NAME_OFFSET = 30
    private const val STORED = 0

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val BYTES_PER_SHORT = 2
    private const val BITS_PER_SHORT = 16

    /** Inspects [header] (the first bytes of a file) and reports its format. */
    public fun detect(header: ByteArray): FormatDetection {
        val window = if (header.size > HEADER_SIZE) header.copyOf(HEADER_SIZE) else header
        return when {
            window.indexOf(PDF_MARKER) >= 0 -> {
                FormatDetection.Detected(BookFormat.PDF)
            }

            window.startsWith(ZIP_LOCAL_HEADER) -> {
                if (hasEpubMimetypeEntry(window)) {
                    FormatDetection.Detected(BookFormat.EPUB)
                } else {
                    FormatDetection.ZipArchive
                }
            }

            else -> {
                FormatDetection.Unsupported
            }
        }
    }

    private fun hasEpubMimetypeEntry(zip: ByteArray): Boolean {
        if (zip.size < NAME_OFFSET) return false
        val method = zip.readLittleEndianShort(COMPRESSION_METHOD_OFFSET)
        val compressedSize = zip.readLittleEndianInt(COMPRESSED_SIZE_OFFSET)
        val nameLength = zip.readLittleEndianShort(NAME_LENGTH_OFFSET)
        val extraLength = zip.readLittleEndianShort(EXTRA_LENGTH_OFFSET)
        val contentOffset = NAME_OFFSET + nameLength + extraLength
        return method == STORED &&
            nameLength == EPUB_MIMETYPE_NAME.size &&
            zip.regionEquals(NAME_OFFSET, EPUB_MIMETYPE_NAME) &&
            compressedSize.toLong() == EPUB_MIMETYPE_VALUE.size.toLong() &&
            zip.regionEquals(contentOffset, EPUB_MIMETYPE_VALUE)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = regionEquals(0, prefix)

    private fun ByteArray.regionEquals(
        offset: Int,
        expected: ByteArray,
    ): Boolean {
        if (offset < 0 || offset + expected.size > size) return false
        return expected.indices.all { this[offset + it] == expected[it] }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int =
        (0..size - needle.size).firstOrNull { regionEquals(it, needle) } ?: -1

    private fun ByteArray.readLittleEndianShort(offset: Int): Int =
        (this[offset].toInt() and BYTE_MASK) or ((this[offset + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

    private fun ByteArray.readLittleEndianInt(offset: Int): Int =
        readLittleEndianShort(offset) or (readLittleEndianShort(offset + BYTES_PER_SHORT) shl BITS_PER_SHORT)
}
