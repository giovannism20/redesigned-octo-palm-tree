package com.giovannimartins.rreader.textkit.epub

import java.io.File
import kotlin.time.TimeSource

/**
 * Turns an EPUB 2 or EPUB 3 file into a [NormalizedBook][com.giovannimartins.rreader.textkit.model.NormalizedBook].
 *
 * The file is untrusted input. Entries are read into memory by exact name and nothing is written to disk (SR-1),
 * reading stops at the [limits] (SR-2), and XML is parsed without DTDs or external entities (SR-3).
 *
 * How a book is built:
 * - `META-INF/container.xml` names the package document, which gives the metadata (first `dc:title`, every
 *   `dc:creator`, `dc:language`, `dc:description` as plain text), the manifest and the spine.
 * - Chapters follow the spine, skipping `linear="no"` items, and come from the EPUB 3 navigation document (its `toc`
 *   nav), else from the EPUB 2 NCX. An entry for a whole document starts a chapter there, and the documents after it
 *   join that chapter until the next start, because books often split a chapter across files. An entry for an element
 *   (`file.xhtml#id`) starts a chapter at that element, so books that keep many chapters in a few large files, as
 *   Project Gutenberg's do, are split into their real chapters; the headings printed right before the element join
 *   it. Of a nested table of contents, only the levels that give chapters of a reasonable length count, and short
 *   sections such as title-page lines join a neighbour in the same document. Sections longer than about an hour of
 *   listening are split at their chapter headings (`CAPÍTULO I`, `II`, ...). When the table of contents points to no
 *   spine document, every document starts a chapter. Titles come from the table of contents, else from headings.
 *   Chapters without readable text are dropped. The internal `ChapterAssembler` has the details.
 * - Text keeps paragraphs and skips scripts, styles, navigation, ruby annotations and footnotes (references and
 *   bodies); whitespace is collapsed and invisible characters removed.
 * - Books from Project Gutenberg lose the English header, production credits and license that Gutenberg adds around
 *   them ([GutenbergBoilerplate][com.giovannimartins.rreader.textkit.gutenberg.GutenbergBoilerplate]), so they are
 *   not read aloud; chapters left empty are dropped.
 * - The cover is the EPUB 3 `cover-image` item, else the item named by EPUB 2 `<meta name="cover">`, if it is an image.
 * - DRM is detected from `META-INF/rights.xml` or from `META-INF/encryption.xml` declaring anything other than font
 *   obfuscation.
 *
 * Instances keep no state between calls and can be shared across threads.
 */
public class EpubParser internal constructor(
    private val limits: ArchiveLimits,
    private val timeSource: TimeSource,
) {
    public constructor(limits: ArchiveLimits = ArchiveLimits()) : this(limits, TimeSource.Monotonic)

    /**
     * Parses [file]; [fallbackTitle] is used when the package has no usable title (the file name is a good choice).
     * Never throws for bad input: every problem with the file is reported as an [EpubResult.Failure], a parse that
     * runs longer than [ArchiveLimits.maxParseTime] included. Throws [InterruptedException] when its thread is
     * interrupted, which is how a caller cancels a parse.
     */
    public fun parse(
        file: File,
        fallbackTitle: String,
    ): EpubResult {
        val result =
            try {
                val budget = ParseBudget(limits.maxParseTime, limits.maxTextChars, timeSource)
                EpubArchive.open(file, limits).use { archive ->
                    EpubResult.Success(EpubBookReader(archive, budget).read(fallbackTitle))
                }
            } catch (failure: EpubParseException) {
                EpubResult.Failure(failure.error)
            }
        // jsoup reads the input of an interrupted thread as if it ended there, so a cancelled parse can look like a
        // damaged or shorter book: it is reported as cancelled instead.
        if (Thread.currentThread().isInterrupted) throw InterruptedException("EPUB parse cancelled")
        return result
    }
}
