package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.textkit.layout.LayoutLine
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/** The resumable text recognition stage, with a fake recognizer (the real one needs a device). */
@RunWith(RobolectricTestRunner::class)
class PdfOcrTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files by lazy { BookFileStore(folder.root) }
    private val store by lazy { OcrPageStore(files) }
    private val source by lazy { folder.newFile("source.pdf") }
    private val progress = mutableListOf<Pair<Int, Int>>()

    @Test
    fun `recognizes the pictured pages without text, in order, and reports each one`() {
        val recognizer = FakeRecognizer()
        val content = content(text(0), picture(1), text(2), picture(3), picture(4))

        val result = ocr(recognizer).recognize(BOOK_ID, source, content, ::onPage)

        assertThat(recognizer.requested).containsExactly(1, 3, 4).inOrder()
        assertThat(progress).containsExactly(1 to 3, 2 to 3, 3 to 3).inOrder()
        val pages = (result as PdfExtraction.Extracted).content.pages
        assertThat(pages.map { it.lines.single().text })
            .containsExactly(TEXT_LAYER, "Página 1 lida", TEXT_LAYER, "Página 3 lida", "Página 4 lida")
            .inOrder()
        assertThat(recognizer.closed).isTrue()
    }

    @Test
    fun `leaves alone a book whose pages have text, and pages without text that draw no picture`() {
        val recognizer = FakeRecognizer()
        val short = PageLayout(2, 420f, 595f, listOf(LayoutLine("PARTE I", 180f, 200f, 240f, 220f, 18f)))
        val content = PdfContent(listOf(text(0), blank(1), short), emptyList(), PdfInfo(), picturedPages = emptySet())

        val result = ocr(recognizer).recognize(BOOK_ID, source, content, ::onPage)

        assertThat(result).isEqualTo(PdfExtraction.Extracted(content))
        assertThat(recognizer.opened).isEqualTo(0)
        assertThat(progress).isEmpty()
    }

    @Test
    fun `stores each page at once, and a new run resumes after the pages stored`() {
        val interrupted = FakeRecognizer(interruptAt = 3)
        val content = content(picture(0), picture(1), text(2), picture(3), picture(4))

        assertThrows(InterruptedException::class.java) {
            ocr(interrupted).recognize(BOOK_ID, source, content, ::onPage)
        }
        assertThat(listOf(0, 1, 3, 4).map { store.read(BOOK_ID, it) != null })
            .containsExactly(true, true, false, false)
            .inOrder()

        progress.clear()
        val resumed = FakeRecognizer()
        val result = ocr(resumed).recognize(BOOK_ID, source, content, ::onPage)

        assertThat(resumed.requested).containsExactly(3, 4).inOrder()
        assertThat(progress).containsExactly(3 to 4, 4 to 4).inOrder()
        assertThat((result as PdfExtraction.Extracted).content.pages.map { it.lines.single().text })
            .containsExactly("Página 0 lida", "Página 1 lida", TEXT_LAYER, "Página 3 lida", "Página 4 lida")
            .inOrder()
    }

    @Test
    fun `recognizes a page again when its stored file cannot be read back`() {
        files.ocrPageFile(BOOK_ID, 1).apply { parentFile!!.mkdirs() }.writeText("{ danificado")
        val recognizer = FakeRecognizer()

        ocr(recognizer).recognize(BOOK_ID, source, content(text(0), picture(1)), ::onPage)

        assertThat(recognizer.requested).containsExactly(1)
        assertThat(
            store
                .read(BOOK_ID, 1)
                ?.page
                ?.lines
                ?.single()
                ?.text,
        ).isEqualTo("Página 1 lida")
    }

    @Test
    fun `SR-4 refuses a book with more pages to recognize than the limit, before drawing any`() {
        val recognizer = FakeRecognizer()
        val content = content(picture(0), picture(1), picture(2))

        val result = ocr(recognizer, OcrLimits(maxPages = 2)).recognize(BOOK_ID, source, content, ::onPage)

        assertThat(result).isEqualTo(PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.MAX_OCR_PAGES)))
        assertThat(recognizer.opened).isEqualTo(0)
        assertThat(ocr(FakeRecognizer(), OcrLimits(maxPages = 3)).recognize(BOOK_ID, source, content, ::onPage))
            .isInstanceOf(PdfExtraction.Extracted::class.java)
    }

    @Test
    fun `SR-4 stops at the time budget, counting the time that stored pages took`() {
        val clock = TestTimeSource()
        val slow = FakeRecognizer(onRecognize = { clock += 10.minutes })
        val content = content(picture(0), picture(1), picture(2), picture(3))
        val limits = OcrLimits(timeBudget = 25.minutes)

        val first = ocr(slow, limits, clock).recognize(BOOK_ID, source, content, ::onPage)

        assertThat(first).isEqualTo(PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET)))
        assertThat(slow.requested).containsExactly(0, 1, 2).inOrder()

        val again = FakeRecognizer()
        assertThat(ocr(again, limits, clock).recognize(BOOK_ID, source, content, ::onPage))
            .isEqualTo(PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.TIME_BUDGET)))
        assertThat(again.requested).isEmpty()
    }

    @Test
    fun `ends with a typed error when the file cannot be drawn or the recognizer fails`() {
        val content = content(picture(0))
        val unopenable = PageTextRecognizer { source -> throw OcrException(PdfError.Damaged, IOException(source.name)) }
        val failing = failingWith(OcrException(PdfError.TextRecognitionFailed))
        val crashing = failingWith(IllegalStateException("native failure"))
        val outOfMemory = failingWith(OutOfMemoryError())

        assertThat(ocr(unopenable).recognize(BOOK_ID, source, content, ::onPage))
            .isEqualTo(PdfExtraction.Failed(PdfError.Damaged))
        assertThat(ocr(failing).recognize(BOOK_ID, source, content, ::onPage))
            .isEqualTo(PdfExtraction.Failed(PdfError.TextRecognitionFailed))
        assertThat(ocr(crashing).recognize(BOOK_ID, source, content, ::onPage))
            .isEqualTo(PdfExtraction.Failed(PdfError.TextRecognitionFailed))
        assertThat(ocr(outOfMemory).recognize(BOOK_ID, source, content, ::onPage))
            .isEqualTo(PdfExtraction.Failed(PdfError.LimitExceeded(PdfLimit.MEMORY)))
        assertThat(failing.closed).isTrue()
    }

    @Test
    fun `one page at a time is recognized in the whole app, and waiting can be cancelled`() {
        val gate = OcrGate()
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread { gate.withPermit { holding.countDown().also { release.await() } } }
        holding.await(WAIT_SECONDS, TimeUnit.SECONDS)
        var waiterFailure: Throwable? = null
        val waiter =
            thread {
                try {
                    ocr(FakeRecognizer(), gate = gate).recognize(BOOK_ID, source, content(picture(0)), ::onPage)
                } catch (e: InterruptedException) {
                    waiterFailure = e
                }
            }

        waiter.join(SHORT_WAIT_MILLIS)
        assertThat(waiter.isAlive).isTrue()
        waiter.interrupt()
        waiter.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        release.countDown()
        holder.join()

        assertThat(waiterFailure).isInstanceOf(InterruptedException::class.java)
        assertThat(store.read(BOOK_ID, 0)).isNull()
    }

    private fun failingWith(failure: Throwable) = FakeRecognizer(onRecognize = { throw failure })

    private fun onPage(
        page: Int,
        pageCount: Int,
    ) {
        progress += page to pageCount
    }

    private fun ocr(
        recognizer: PageTextRecognizer,
        limits: OcrLimits = OcrLimits(),
        timeSource: TimeSource = TimeSource.Monotonic,
        gate: OcrGate = OcrGate(),
    ) = PdfOcr(recognizer, store, gate, limits, timeSource)

    /** A document whose pages are [pages]; those without lines are pictures, as scanned pages are. */
    private fun content(vararg pages: PageLayout) =
        PdfContent(
            pages = pages.toList(),
            outline = emptyList(),
            info = PdfInfo(),
            picturedPages = pages.filter { it.lines.isEmpty() }.map { it.index }.toSet(),
        )

    private fun text(index: Int) =
        PageLayout(index, 420f, 595f, listOf(LayoutLine(TEXT_LAYER, 50f, 100f, 370f, 112f, 12f)))

    private fun picture(index: Int) = PageLayout(index, 420f, 595f, emptyList())

    private fun blank(index: Int) = PageLayout(index, 420f, 595f, emptyList())

    /** Reads "Página N lida" on page N; [interruptAt] simulates the import being cancelled on that page. */
    private class FakeRecognizer(
        private val interruptAt: Int? = null,
        private val onRecognize: () -> Unit = {},
    ) : PageTextRecognizer {
        val requested = mutableListOf<Int>()
        var opened = 0
        var closed = false

        override fun open(source: File): PageTextRecognizer.Session {
            opened++
            return object : PageTextRecognizer.Session {
                override fun recognize(pageIndex: Int): PageLayout {
                    if (pageIndex == interruptAt) throw InterruptedException("import cancelled")
                    onRecognize()
                    requested += pageIndex
                    val line = LayoutLine("Página $pageIndex lida", 50f, 100f, 370f, 112f, 12f)
                    return PageLayout(pageIndex, 420f, 595f, listOf(line))
                }

                override fun close() {
                    closed = true
                }
            }
        }
    }

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
        const val TEXT_LAYER = "Uma linha da camada de texto, com bastante coisa escrita nela."
        const val WAIT_SECONDS = 5L
        const val SHORT_WAIT_MILLIS = 200L
    }
}
