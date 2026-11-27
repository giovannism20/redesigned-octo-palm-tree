package com.giovannimartins.rreader.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.pdf.OcrLimits
import com.giovannimartins.rreader.pdf.PageTextRecognizer
import com.giovannimartins.rreader.pdf.PdfCoverRenderer
import com.giovannimartins.rreader.pdf.PdfError
import com.giovannimartins.rreader.pdf.PdfFixture
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.FOOTER_BASELINE
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.HEADER_BASELINE
import com.giovannimartins.rreader.pdf.PdfFixture.Companion.INDENT
import com.giovannimartins.rreader.pdf.PdfLayoutExtractor
import com.giovannimartins.rreader.pdf.PdfLimit
import com.giovannimartins.rreader.pdf.PdfOcr
import com.giovannimartins.rreader.pdf.ScriptedRecognizer
import com.giovannimartins.rreader.pdf.pdfOcr
import com.giovannimartins.rreader.pdf.scannedPage
import com.giovannimartins.rreader.textkit.layout.LayoutBookBuilder
import com.giovannimartins.rreader.textkit.layout.LayoutError
import com.giovannimartins.rreader.textkit.model.BookMetadata
import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.CoverImage
import com.giovannimartins.rreader.textkit.model.NormalizedBook
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfBookParserTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cover = CoverImage("image/jpeg", byteArrayOf(1, 2, 3))

    @Test
    fun `imports a typeset PDF with its outline, without running headers and page numbers`() {
        val outcome = parse(typesetBook())

        assertThat(outcome)
            .isEqualTo(
                ParseOutcome.Parsed(
                    NormalizedBook(
                        BookMetadata("Dom Casmurro", listOf("Machado de Assis"), "pt-BR"),
                        listOf(
                            Chapter.fromParagraphs(
                                "Do título",
                                listOf(
                                    "CAPÍTULO PRIMEIRO",
                                    "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem " +
                                        "um rapaz que me cumprimentou e quis fazê-lo de novo na estação seguinte.",
                                ),
                            ),
                            Chapter.fromParagraphs(
                                "Do livro",
                                listOf(
                                    "CAPÍTULO SEGUNDO",
                                    "No dia seguinte voltei a vê-lo na plataforma, e ele continuou a falar " +
                                        "sem parar, de modo que a viagem pareceu curta.",
                                    "— Boa noite — disse ele.",
                                ),
                            ),
                        ),
                        cover,
                    ),
                ),
            )
    }

    @Test
    fun `falls back to the file name when the document information is meaningless`() {
        val book =
            typesetBook().apply {
                title = "Microsoft Word - livro.doc"
                author = "Usuário"
                language = "x-none"
            }

        val metadata = (parse(book, fallbackTitle = "livro do vovô") as ParseOutcome.Parsed).book.metadata

        assertThat(metadata).isEqualTo(BookMetadata("livro do vovô"))
    }

    @Test
    fun `imports a scanned PDF from what text recognition reads, with chapters, paragraphs and its progress`() {
        val scan =
            PdfFixture(context).apply {
                title = "Dom Casmurro"
                repeat(3) { picturePage() }
            }
        val progress = mutableListOf<ImportProgress>()

        val outcome = parse(scan, recognizer = ScriptedRecognizer(scannedBook()), progress = progress::add)

        assertThat(outcome)
            .isEqualTo(
                ParseOutcome.Parsed(
                    NormalizedBook(
                        BookMetadata("Dom Casmurro"),
                        listOf(
                            Chapter.fromParagraphs(
                                "CAPÍTULO I",
                                listOf(
                                    "CAPÍTULO I",
                                    "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem um " +
                                        "rapaz que me cumprimentou e quis fazê-lo de novo na estação seguinte.",
                                    "No dia seguinte voltei a vê-lo na plataforma, e ele continuou a falar sem " +
                                        "parar, de modo que a viagem pareceu curta.",
                                ),
                            ),
                            Chapter.fromParagraphs("CAPÍTULO II", listOf("CAPÍTULO II", "— Boa noite — disse ele.")),
                        ),
                        cover,
                    ),
                ),
            )
        assertThat(progress)
            .containsExactly(
                ImportProgress.RecognizingText(1, 3),
                ImportProgress.RecognizingText(2, 3),
                ImportProgress.RecognizingText(3, 3),
            ).inOrder()
    }

    @Test
    fun `imports a mixed PDF in page order, recognizing only its scanned page`() {
        val mixed =
            PdfFixture(context).apply {
                page {
                    line("Uma noite destas, vindo da cidade para o", x = INDENT)
                    line("Engenho Novo, encontrei no trem um rapaz")
                    line("aqui do bairro, que eu conheço de vista e")
                }
                picturePage()
                page {
                    line("No dia seguinte voltei a vê-lo na plataforma.", x = INDENT)
                    line("Ele falava sem parar, de modo que a viagem")
                    line("pareceu curta.")
                }
            }
        val recognizer =
            ScriptedRecognizer(
                listOf(
                    scannedPage(1) {
                        paragraph("de chapéu, que me cumprimentou e quis fazê-", "lo de novo.", continued = true)
                    },
                ),
            )

        val book = (parse(mixed, recognizer = recognizer) as ParseOutcome.Parsed).book

        assertThat(recognizer.requested).containsExactly(1)
        assertThat(
            book.chapters
                .single()
                .text
                .split("\n\n"),
        ).containsExactly(
            "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem um rapaz aqui do bairro, " +
                "que eu conheço de vista e de chapéu, que me cumprimentou e quis fazê-lo de novo.",
            "No dia seguinte voltei a vê-lo na plataforma. Ele falava sem parar, de modo que a viagem pareceu curta.",
        ).inOrder()
    }

    @Test
    fun `explains scanned PDFs where nothing readable is recognized, password-protected and damaged PDFs`() {
        val scanned = PdfFixture(context).apply { repeat(3) { picturePage() } }
        val locked =
            PdfFixture(context).apply {
                userPassword = "segredo"
                page { line("Texto protegido.") }
            }
        val damaged = folder.newFile().apply { writeText("%PDF-1.4 truncado") }

        assertThat(parse(scanned)).isEqualTo(ParseOutcome.Failed(ImportFailure.SCANNED_PDF))
        assertThat(parse(locked)).isEqualTo(ParseOutcome.Failed(ImportFailure.PASSWORD_PROTECTED))
        assertThat(parser().parse(ParseRequest(BOOK_ID, damaged, "x")))
            .isEqualTo(ParseOutcome.Failed(ImportFailure.CORRUPTED))
    }

    @Test
    fun `explains scanned PDFs with more pages to recognize than the limit`() {
        val scanned = PdfFixture(context).apply { repeat(3) { picturePage() } }
        val recognizer = ScriptedRecognizer()

        val outcome =
            parser(ocr = pdfOcr(recognizer, folder.root, OcrLimits(maxPages = 2)))
                .parse(ParseRequest(BOOK_ID, scanned.writeTo(folder.newFile()), "x"))

        assertThat(outcome).isEqualTo(ParseOutcome.Failed(ImportFailure.TOO_MANY_SCANNED_PAGES))
        assertThat(recognizer.requested).isEmpty()
    }

    @Test
    fun `imports the book without a cover when the first page cannot be drawn`() {
        val source = typesetBook().writeTo(folder.newFile())

        val book =
            (
                parser(
                    coverRenderer = { null },
                ).parse(ParseRequest(BOOK_ID, source, "x")) as ParseOutcome.Parsed
            ).book

        assertThat(book.cover).isNull()
    }

    @Test
    fun `maps every PDF and layout error to a user-facing failure`() {
        assertThat(PdfError.Damaged.toImportFailure()).isEqualTo(ImportFailure.CORRUPTED)
        assertThat(PdfError.PasswordProtected.toImportFailure()).isEqualTo(ImportFailure.PASSWORD_PROTECTED)
        assertThat(PdfError.TextRecognitionFailed.toImportFailure()).isEqualTo(ImportFailure.OCR_FAILED)
        assertThat(PdfError.LimitExceeded(PdfLimit.MAX_PAGES).toImportFailure()).isEqualTo(ImportFailure.TOO_MANY_PAGES)
        assertThat(PdfError.LimitExceeded(PdfLimit.MAX_OCR_PAGES).toImportFailure())
            .isEqualTo(ImportFailure.TOO_MANY_SCANNED_PAGES)
        listOf(PdfLimit.TIME_BUDGET, PdfLimit.MAX_GLYPHS, PdfLimit.MEMORY).forEach { limit ->
            assertThat(PdfError.LimitExceeded(limit).toImportFailure()).isEqualTo(ImportFailure.TOO_COMPLEX)
        }
        assertThat(LayoutError.ScannedDocument.toImportFailure()).isEqualTo(ImportFailure.SCANNED_PDF)
        assertThat(LayoutError.NoReadableText.toImportFailure()).isEqualTo(ImportFailure.NO_READABLE_TEXT)
    }

    /**
     * Three pages with a running header and page numbers, an outline with two chapters, chapter headings in a large
     * font, first-line indents, a paragraph that continues on the next page, enclisis broken across lines and dialogue.
     */
    private fun typesetBook() =
        PdfFixture(context).apply {
            title = "Dom Casmurro"
            author = "Machado de Assis"
            language = "pt-BR"
            page {
                at(HEADER_BASELINE, "DOM CASMURRO")
                line("CAPÍTULO PRIMEIRO", x = 120f, size = 18f)
                skip()
                line("Uma noite destas, vindo da cidade para o", x = INDENT)
                line("Engenho Novo, encontrei no trem um rapaz")
                line("que me cumprimentou e quis fazê-")
                line("lo de novo na estação seguinte.")
                at(FOOTER_BASELINE, "1", x = 205f)
            }
            page {
                at(HEADER_BASELINE, "DOM CASMURRO")
                line("CAPÍTULO SEGUNDO", x = 125f, size = 18f)
                skip()
                line("No dia seguinte voltei a vê-lo na plataforma,", x = INDENT)
                line("e ele continuou a falar sem parar, de modo que")
                at(FOOTER_BASELINE, "2", x = 205f)
            }
            page {
                at(HEADER_BASELINE, "DOM CASMURRO")
                line("a viagem pareceu curta.")
                line("— Boa noite — disse ele.", x = INDENT)
                at(FOOTER_BASELINE, "3", x = 205f)
            }
            bookmark("Do título", 0)
            bookmark("Do livro", 1)
        }

    /** What text recognition reads on the three scanned pages of the book [typesetBook] prints. */
    private fun scannedBook() =
        listOf(
            scannedPage(0) {
                header("DOM CASMURRO")
                heading("CAPÍTULO I")
                paragraph(
                    "Uma noite destas, vindo da cidade para o",
                    "Engenho Novo, encontrei no trem um rapaz",
                    "que me cumprimentou e quis fazê-",
                    "lo de novo na estação seguinte.",
                )
                pageNumber("1")
            },
            scannedPage(1) {
                header("DOM CASMURRO")
                paragraph(
                    "No dia seguinte voltei a vê-lo na plataforma,",
                    "e ele continuou a falar sem parar, de modo",
                    "que a viagem pareceu curta.",
                )
                pageNumber("2")
            },
            scannedPage(2) {
                header("DOM CASMURRO")
                heading("CAPÍTULO II")
                paragraph("— Boa noite — disse ele.")
                pageNumber("3")
            },
        )

    private fun parse(
        fixture: PdfFixture,
        fallbackTitle: String = "livro",
        recognizer: PageTextRecognizer = ScriptedRecognizer(),
        progress: ImportProgressListener = ImportProgressListener.NONE,
    ): ParseOutcome =
        parser(ocr = pdfOcr(recognizer, folder.root))
            .parse(ParseRequest(BOOK_ID, fixture.writeTo(folder.newFile()), fallbackTitle, progress))

    private fun parser(
        coverRenderer: PdfCoverRenderer = PdfCoverRenderer { cover },
        ocr: PdfOcr = pdfOcr(ScriptedRecognizer(), folder.root),
    ) = PdfBookParser(PdfLayoutExtractor(context), ocr, coverRenderer, LayoutBookBuilder())

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
