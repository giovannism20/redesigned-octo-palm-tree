package com.giovannimartins.rreader.importer

import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.textkit.epub.ArchiveLimit
import com.giovannimartins.rreader.textkit.epub.EpubError
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class EpubBookParserTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `parses a real EPUB into chapters and metadata`() {
        val file = folder.newFile("book.epub").apply { writeBytes(MinimalEpub.bytes()) }

        val outcome = EpubBookParser().parse(ParseRequest(BOOK_ID, file, fallbackTitle = "fallback"))

        val book = (outcome as ParseOutcome.Parsed).book
        assertThat(book.metadata.title).isEqualTo("Iracema")
        assertThat(book.metadata.authors).containsExactly("José de Alencar")
        assertThat(book.chapters.map { it.title }).containsExactly("I", "II").inOrder()
        assertThat(book.chapters[1].text).contains("afouta jangada")
    }

    @Test
    fun `maps every EPUB error to a user-facing failure`() {
        assertThat(EpubError.DrmProtected.toImportFailure()).isEqualTo(ImportFailure.DRM_PROTECTED)
        assertThat(
            EpubError.LimitExceeded(ArchiveLimit.MAX_TOTAL_BYTES).toImportFailure(),
        ).isEqualTo(ImportFailure.TOO_LARGE)
        assertThat(EpubError.NoReadableText.toImportFailure()).isEqualTo(ImportFailure.NO_READABLE_TEXT)
        assertThat(EpubError.TooComplex.toImportFailure()).isEqualTo(ImportFailure.TOO_COMPLEX)
        listOf(
            EpubError.NotAZipArchive,
            EpubError.MissingContainer,
            EpubError.MissingPackageDocument,
            EpubError.MalformedDocument("OEBPS/c0.xhtml"),
        ).forEach { assertThat(it.toImportFailure()).isEqualTo(ImportFailure.CORRUPTED) }
    }

    @Test
    fun `turns running out of memory or stack, and unexpected errors, into failures`() {
        val file = folder.newFile("book.epub")

        fun outcomeWhenParsing(error: Throwable) =
            EpubBookParser { _, _ -> throw error }.parse(ParseRequest(BOOK_ID, file, fallbackTitle = "x"))

        assertThat(outcomeWhenParsing(OutOfMemoryError())).isEqualTo(ParseOutcome.Failed(ImportFailure.TOO_LARGE))
        assertThat(outcomeWhenParsing(StackOverflowError())).isEqualTo(ParseOutcome.Failed(ImportFailure.TOO_COMPLEX))
        assertThat(
            outcomeWhenParsing(IllegalStateException("jsoup")),
        ).isEqualTo(ParseOutcome.Failed(ImportFailure.CORRUPTED))
        assertThrows(InterruptedException::class.java) { outcomeWhenParsing(InterruptedException()) }
    }

    @Test
    fun `reports a damaged file as corrupted`() {
        val file = folder.newFile("broken.epub").apply { writeText("not a zip") }

        assertThat(EpubBookParser().parse(ParseRequest(BOOK_ID, file, "x")))
            .isEqualTo(ParseOutcome.Failed(ImportFailure.CORRUPTED))
    }

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
