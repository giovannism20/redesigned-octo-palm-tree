package com.giovannimartins.rreader.textkit.epub

import java.io.File

/**
 * Turns an EPUB 2 or EPUB 3 file into a [NormalizedBook][com.giovannimartins.rreader.textkit.model.NormalizedBook].
 *
 * The file is untrusted input. Entries are read into memory by exact name and nothing is written to disk (SR-1),
 * reading stops at the [limits] (SR-2), and XML is parsed without DTDs or external entities (SR-3).
 *
 * How a book is built:
 * - `META-INF/container.xml` names the package document, which gives the metadata (first `dc:title`, every
 *   `dc:creator`, `dc:language`, `dc:description` as plain text), the manifest and the spine.
 * - Chapters follow the spine, skipping `linear="no"` items. Titles come from the EPUB 3 navigation document (its
 *   `toc` nav), else from the EPUB 2 NCX. A spine document the table of contents points to starts a chapter; the
 *   documents after it join that chapter until the next one it points to, because books often split a chapter across
 *   files. When the table of contents points to no spine document, every document is a chapter titled by its first
 *   `h1`–`h3` heading. Chapters without readable text are dropped.
 * - Text keeps paragraphs and skips scripts, styles, navigation, ruby annotations and footnotes (references and
 *   bodies); whitespace is collapsed and invisible characters removed.
 * - The cover is the EPUB 3 `cover-image` item, else the item named by EPUB 2 `<meta name="cover">`, if it is an image.
 * - DRM is detected from `META-INF/rights.xml` or from `META-INF/encryption.xml` declaring anything other than font
 *   obfuscation.
 *
 * Instances keep no state between calls and can be shared across threads.
 */
public class EpubParser(
    private val limits: ArchiveLimits = ArchiveLimits(),
) {
    /**
     * Parses [file]; [fallbackTitle] is used when the package has no usable title (the file name is a good choice).
     * Never throws for bad input: every problem with the file is reported as an [EpubResult.Failure].
     */
    public fun parse(
        file: File,
        fallbackTitle: String,
    ): EpubResult =
        try {
            EpubArchive.open(file, limits).use { archive ->
                EpubResult.Success(EpubBookReader(archive).read(fallbackTitle))
            }
        } catch (failure: EpubParseException) {
            EpubResult.Failure(failure.error)
        }
}
