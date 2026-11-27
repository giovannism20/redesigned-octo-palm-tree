package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.textkit.epub.EpubError
import com.giovannimartins.rreader.textkit.epub.EpubParser
import com.giovannimartins.rreader.textkit.epub.EpubResult
import javax.inject.Inject

/** Adapts the `:textkit` EPUB parser to the importer, translating its errors into user-facing failures. */
class EpubBookParser internal constructor(
    private val parser: EpubParser,
) : BookParser {
    @Inject
    constructor() : this(EpubParser())

    override fun parse(request: ParseRequest): ParseOutcome =
        when (val result = parser.parse(request.source, request.fallbackTitle)) {
            is EpubResult.Success -> ParseOutcome.Parsed(result.book)
            is EpubResult.Failure -> ParseOutcome.Failed(result.error.toImportFailure())
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
