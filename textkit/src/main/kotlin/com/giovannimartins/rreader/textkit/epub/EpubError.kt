package com.giovannimartins.rreader.textkit.epub

/**
 * Why an EPUB file could not become a book. Each case is meant to map to one user-facing message.
 *
 * Paths are archive entry names, useful in tests and debug builds; like any file name they must not reach release
 * logs (SR-8).
 */
public sealed interface EpubError {
    /** The file cannot be opened as a ZIP archive: it is not one, it is truncated or damaged, or it is unreadable. */
    public data object NotAZipArchive : EpubError

    /** The archive has no `META-INF/container.xml`, so it is a plain ZIP rather than an EPUB. */
    public data object MissingContainer : EpubError

    /** The container names a package document (OPF) that is not in the archive. */
    public data object MissingPackageDocument : EpubError

    /** The book's content is encrypted with DRM, which R Reader cannot open. */
    public data object DrmProtected : EpubError

    /** The archive exceeds [limit]; it is unusually large or a zip bomb. */
    public data class LimitExceeded(
        val limit: ArchiveLimit,
    ) : EpubError

    /** The entry [path] is damaged or lacks the structure the format requires. */
    public data class MalformedDocument(
        val path: String,
    ) : EpubError

    /** The book has no text that can be read aloud, for example because it only contains images. */
    public data object NoReadableText : EpubError
}
