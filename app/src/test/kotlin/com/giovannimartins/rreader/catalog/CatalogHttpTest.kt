package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.net.FakeTransport
import com.giovannimartins.rreader.net.FakeTransport.Companion.EPUB
import com.giovannimartins.rreader.net.FakeTransport.Companion.breakingBody
import com.giovannimartins.rreader.net.FakeTransport.Companion.ok
import com.giovannimartins.rreader.net.FakeTransport.Companion.redirect
import com.giovannimartins.rreader.net.FakeTransport.Companion.status
import com.giovannimartins.rreader.net.FetchFailure
import com.giovannimartins.rreader.net.FetchResult
import com.giovannimartins.rreader.net.HttpsClient
import com.giovannimartins.rreader.net.HttpsRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException

/** SR-5: what the catalogue's HTTP client sends, follows and accepts. Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class CatalogHttpTest {
    private val transport = FakeTransport()
    private val http = CatalogHttp(transport, Dispatchers.Unconfined)
    private val sink = ByteArrayOutputStream()

    @Test
    fun `fetches an allowed address with the app's user agent and nothing that identifies anyone`() {
        transport.answer(BOOK) { ok(BYTES, EPUB) }

        val result = fetch(BOOK)

        assertThat(result).isEqualTo(FetchResult.Received(BYTES.size.toLong()))
        assertThat(sink.toByteArray()).isEqualTo(BYTES)
        val headers = transport.requests.single().headers
        assertThat(headers).containsExactly("User-Agent", CatalogUserAgent.value, "Accept", EPUB)
    }

    @Test
    fun `follows redirects within the allowlist`() {
        transport.answer(BOOK) { redirect("/cache/epub/55752/pg55752-images-3.epub") }
        transport.answer(FILE) { ok(BYTES, "$EPUB; charset=binary") }

        val result = fetch(BOOK)

        assertThat(result).isEqualTo(FetchResult.Received(BYTES.size.toLong()))
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK, FILE).inOrder()
    }

    @Test
    fun `follows every kind of redirect`() {
        listOf(301, 303, 307, 308).forEach { code ->
            transport.answer(BOOK) { redirect(FILE, code) }
            transport.answer(FILE) { ok(BYTES, EPUB) }

            assertThat(fetch(BOOK)).isEqualTo(FetchResult.Received(BYTES.size.toLong()))
        }
    }

    @Test
    fun `follows a redirect to the official mirror but not to another Gutenberg host`() {
        val mirror = "https://gutenberg.pglaf.org/cache/epub/55752/pg55752-images-3.epub"
        transport.answer(BOOK) { redirect(mirror) }
        transport.answer(mirror) { ok(BYTES, EPUB) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Received(BYTES.size.toLong()))

        transport.answer(BOOK) { redirect("https://aleph.gutenberg.org/cache/epub/55752/pg55752-images-3.epub") }
        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
    }

    @Test
    fun `refuses a redirect off the allowlist without contacting it`() {
        transport.answer(BOOK) { redirect("https://mirror.example/pg55752.epub") }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(transport.requests.map { it.address }).containsExactly(BOOK)
    }

    @Test
    fun `refuses a redirect to cleartext, even on the same host`() {
        transport.answer(BOOK) { redirect("http://www.gutenberg.org/cache/epub/55752/pg55752-images-3.epub") }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `refuses a redirect without a destination`() {
        transport.answer(BOOK) { redirect(null) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
    }

    @Test
    fun `refuses an address off the allowlist before any request`() {
        assertThat(fetch("https://mirror.example/pg55752.epub")).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(fetch("http://www.gutenberg.org/ebooks/55752.epub3.images"))
            .isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `stops after too many redirects`() {
        (0..HttpsClient.MAX_REDIRECTS + 1).forEach { hop ->
            transport.answer("https://www.gutenberg.org/hop/$hop") { redirect("/hop/${hop + 1}") }
        }

        assertThat(fetch("https://www.gutenberg.org/hop/0")).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(transport.requests).hasSize(HttpsClient.MAX_REDIRECTS + 1)
    }

    @Test
    fun `refuses an answer of another type, such as an error page`() {
        transport.answer(BOOK) { ok("<html>Down for maintenance</html>".toByteArray(), "text/html; charset=utf-8") }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.WRONG_TYPE))
        assertThat(sink.size()).isEqualTo(0)
    }

    @Test
    fun `refuses an answer without a type`() {
        transport.answer(BOOK) { ok(BYTES, null) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.WRONG_TYPE))
    }

    @Test
    fun `refuses an announced body larger than allowed without reading it`() {
        val body = FakeTransport.Companion.CountingBody(MAX_BYTES.toInt() + 1)
        transport.answer(BOOK) { ok(body, EPUB, contentLength = MAX_BYTES + 1) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.TOO_LARGE))
        assertThat(body.read).isEqualTo(0)
    }

    @Test
    fun `stops reading a body that grows past the limit`() {
        val body = FakeTransport.Companion.CountingBody(MAX_BYTES.toInt() * 3)
        transport.answer(BOOK) { ok(body, EPUB, contentLength = -1) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.TOO_LARGE))
        assertThat(sink.size().toLong()).isAtMost(MAX_BYTES)
        assertThat(body.read.toLong()).isLessThan(MAX_BYTES * 3)
    }

    @Test
    fun `accepts a body of exactly the limit`() {
        transport.answer(BOOK) { ok(ByteArray(MAX_BYTES.toInt()), EPUB, contentLength = -1) }

        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Received(MAX_BYTES))
    }

    @Test
    fun `a request that cannot reach the server is a network failure`() {
        transport.answer(BOOK) { throw SocketTimeoutException("connect timed out") }

        val result = fetch(BOOK)

        assertThat(result).isEqualTo(FetchResult.Failed(FetchFailure.NETWORK))
        assertThat((result as FetchResult.Failed).failure.retryable).isTrue()
    }

    @Test
    fun `a connection that breaks or ends early is a network failure`() {
        transport.answer(BOOK) { ok(breakingBody(100), EPUB, contentLength = 1_000) }
        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.NETWORK))

        transport.answer(BOOK) { ok(ByteArray(10), EPUB, contentLength = 1_000) }
        assertThat(fetch(BOOK)).isEqualTo(FetchResult.Failed(FetchFailure.NETWORK))
    }

    @Test
    fun `tells missing books, busy servers and refusals apart`() {
        val failures =
            listOf(404, 410, 429, 500, 503, 403, 400, 204).associateWith { code ->
                transport.answer(BOOK) { status(code) }
                (fetch(BOOK) as FetchResult.Failed).failure
            }

        assertThat(failures)
            .containsExactly(
                404,
                FetchFailure.NOT_FOUND,
                410,
                FetchFailure.NOT_FOUND,
                429,
                FetchFailure.SERVER,
                500,
                FetchFailure.SERVER,
                503,
                FetchFailure.SERVER,
                403,
                FetchFailure.REFUSED,
                400,
                FetchFailure.REFUSED,
                204,
                FetchFailure.REFUSED,
            )
        assertThat(FetchFailure.entries.filter { it.retryable })
            .containsExactly(FetchFailure.NETWORK, FetchFailure.SERVER, FetchFailure.RESUME_REFUSED)
    }

    @Test
    fun `reports the bytes received and the announced length`() {
        val body = ByteArray(90_000) { it.toByte() }
        val reports = mutableListOf<Pair<Long, Long>>()
        transport.answer(BOOK) { ok(body, EPUB) }

        runBlocking { http.fetch(request(BOOK), sink) { received, total -> reports += received to total } }

        assertThat(reports.map { it.first }).isInOrder()
        assertThat(reports.last()).isEqualTo(body.size.toLong() to body.size.toLong())
    }

    @Test
    fun `the user agent names the app and, when the build has one, a contact`() {
        assertThat(CatalogUserAgent.of("1.2.3", "")).isEqualTo("RReader/1.2.3 (Android book reader)")
        assertThat(CatalogUserAgent.of("1.2.3", " https://example.org/rreader "))
            .isEqualTo("RReader/1.2.3 (Android book reader; +https://example.org/rreader)")
        assertThat(CatalogUserAgent.of("1.2.3", "a\r\nX-Injected: 1"))
            .isEqualTo("RReader/1.2.3 (Android book reader; +aX-Injected: 1)")
    }

    private fun fetch(address: String): FetchResult {
        sink.reset()
        return runBlocking { http.fetch(request(address), sink) }
    }

    private fun request(address: String) = HttpsRequest(address, setOf(EPUB), MAX_BYTES)

    private companion object {
        const val BOOK = "https://www.gutenberg.org/ebooks/55752.epub3.images"
        const val FILE = "https://www.gutenberg.org/cache/epub/55752/pg55752-images-3.epub"
        const val MAX_BYTES = 100_000L
        val BYTES = "PK livro".toByteArray()
    }
}
