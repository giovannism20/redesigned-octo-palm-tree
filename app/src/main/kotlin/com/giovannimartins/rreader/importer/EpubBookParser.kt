package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.textkit.epub.EpubError
import com.giovannimartins.rreader.textkit.epub.EpubParser
import com.giovannimartins.rreader.textkit.epub.EpubResult
import java.io.File
import javax.inject.Inject

/**
 * Adapts the `:textkit` EPUB parser to the importer, translating its errors into user-facing failures. The parser
 * reports bad files as typed errors; whatever else a hostile file still provokes is a failure too, so an import never
 * stops on it: running out of memory means the book is too large, running out of stack that it is too complex, and
 * any other runtime exception that it is damaged.
 */
class EpubBookParser internal constructor(
    private val parseEpub: (source: File, fallbackTitle: String) -> EpubResult,
) : BookParser {
    @Inject
    constructor() : this(EpubParser()::parse)

    @Suppress("TooGenericExceptionCaught") // Any runtime exception from parsing a hostile file is a damaged book.
    override fun parse(request: ParseRequest): ParseOutcome =
        try {
            when (val result = parseEpub(request.source, request.fallbackTitle)) {
                is EpubResult.Success -> ParseOutcome.Parsed(result.book)
                is EpubResult.Failure -> ParseOutcome.Failed(result.error.toImportFailure())
            }
        } catch (e: OutOfMemoryError) {
            unexpected(e, ImportFailure.TOO_LARGE)
        } catch (e: StackOverflowError) {
            unexpected(e, ImportFailure.TOO_COMPLEX)
        } catch (e: RuntimeException) {
            unexpected(e, ImportFailure.CORRUPTED)
        }

    private fun unexpected(
        error: Throwable,
        failure: ImportFailure,
    ): ParseOutcome {
        Diagnostics.recordHandled("parse EPUB", error)
        return ParseOutcome.Failed(failure)
    }
}

/** How each EPUB problem is explained to the user. */
internal fun EpubError.toImportFailure(): ImportFailure =
    when (this) {
        EpubError.DrmProtected -> ImportFailure.DRM_PROTECTED

        is EpubError.LimitExceeded -> ImportFailure.TOO_LARGE

        EpubError.NoReadableText -> ImportFailure.NO_READABLE_TEXT

        EpubError.TooComplex -> ImportFailure.TOO_COMPLEX

        EpubError.NotAZipArchive,
        EpubError.MissingContainer,
        EpubError.MissingPackageDocument,
        is EpubError.MalformedDocument,
        -> ImportFailure.CORRUPTED
    }
