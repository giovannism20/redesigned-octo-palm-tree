package com.giovannimartins.rreader.net

import com.giovannimartins.rreader.net.FakeTransport.Companion.ok
import com.giovannimartins.rreader.net.FakeTransport.Companion.partial
import com.giovannimartins.rreader.net.FakeTransport.Companion.status
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.net.URI

/** Resuming an interrupted download, and the allowlist a client is given. Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class HttpsClientTest {
    private val transport = FakeTransport()
    private val hosts = HostAllowlist { address -> address.isPlainHttps() && address.lowercaseHost() == HOST }
    private val http = HttpsClient(transport, hosts, "RReader/test", Dispatchers.Unconfined)
    private val sink = ByteArrayOutputStream()

    @Test
    fun `asks only for the rest of an interrupted download and appends it`() {
        transport.answer(FILE) { partial(FILE_BYTES.copyOfRange(4, 10), TYPE, "bytes 4-9/10") }
        val reports = mutableListOf<Pair<Long, Long>>()

        val result = runBlocking { http.fetch(request(resumeFrom = 4), sink) { got, total -> reports += got to total } }

        assertThat(result).isEqualTo(FetchResult.Received(10))
        assertThat(sink.toByteArray()).isEqualTo(FILE_BYTES.copyOfRange(4, 10))
        assertThat(transport.requests.single().headers).containsEntry("Range", "bytes=4-")
        assertThat(reports.last()).isEqualTo(10L to 10L)
    }

    @Test
    fun `a whole file sent again, another range or no range at all means starting over`() {
        val answers =
            listOf(
                { ok(FILE_BYTES, TYPE) },
                { partial(FILE_BYTES.copyOfRange(2, 10), TYPE, "bytes 2-9/10") },
                { partial(FILE_BYTES.copyOfRange(4, 8), TYPE, "bytes 4-7/10") },
                { partial(FILE_BYTES.copyOfRange(4, 10), TYPE, null) },
                { partial(FILE_BYTES.copyOfRange(4, 10), TYPE, "bytes 4-9/10", contentLength = 3) },
                { status(416) },
            )

        answers.forEach { answer ->
            transport.answer(FILE, answer)
            assertThat(fetch(request(resumeFrom = 4))).isEqualTo(FetchResult.Failed(FetchFailure.RESUME_REFUSED))
            assertThat(sink.size()).isEqualTo(0)
        }
    }

    @Test
    fun `a partial answer to a request for the whole file is refused`() {
        transport.answer(FILE) { partial(FILE_BYTES.copyOfRange(4, 10), TYPE, "bytes 4-9/10") }

        assertThat(fetch(request())).isEqualTo(FetchResult.Failed(FetchFailure.REFUSED))
        assertThat(transport.requests.single().headers).doesNotContainKey("Range")
    }

    @Test
    fun `the rest of a file larger than allowed is not read`() {
        transport.answer(FILE) { partial(ByteArray(6), TYPE, "bytes 4-1000/1001", contentLength = 997) }

        assertThat(fetch(request(resumeFrom = 4))).isEqualTo(FetchResult.Failed(FetchFailure.TOO_LARGE))
        assertThat(sink.size()).isEqualTo(0)
    }

    @Test
    fun `contacts only the hosts of its allowlist`() {
        assertThat(fetch(request(address = "https://other.example/file.bin")))
            .isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(
            fetch(request(address = "http://$HOST/file.bin")),
        ).isEqualTo(FetchResult.Failed(FetchFailure.BLOCKED))
        assertThat(transport.requests).isEmpty()
        assertThat(hosts.check("https://$HOST:443/file.bin")).isEqualTo(URI("https://$HOST:443/file.bin"))
    }

    private fun fetch(request: HttpsRequest): FetchResult {
        sink.reset()
        return runBlocking { http.fetch(request, sink) }
    }

    private fun request(
        address: String = FILE,
        resumeFrom: Long = 0,
    ) = HttpsRequest(address, setOf(TYPE), MAX_BYTES, resumeFrom)

    private companion object {
        const val HOST = "files.example"
        const val FILE = "https://$HOST/file.bin"
        const val TYPE = "application/octet-stream"
        const val MAX_BYTES = 100L
        val FILE_BYTES = ByteArray(10) { it.toByte() }
    }
}
