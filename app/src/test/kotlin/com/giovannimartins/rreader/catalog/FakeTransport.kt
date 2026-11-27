package com.giovannimartins.rreader.catalog

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.UnknownHostException
import java.util.Collections

/** A transport that answers from a script and records what it was asked; it never touches the network. */
internal class FakeTransport : HttpTransport {
    /** One request the transport received. */
    data class Request(
        val address: String,
        val headers: Map<String, String>,
    )

    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
    private val answers = mutableMapOf<String, () -> HttpResponse>()

    /** Answers GETs of [address] with what [response] returns, or throws. */
    fun answer(
        address: String,
        response: () -> HttpResponse,
    ) {
        answers[address] = response
    }

    override fun get(
        address: URI,
        headers: Map<String, String>,
    ): HttpResponse {
        requests += Request(address.toString(), headers)
        val answer = answers[address.toString()] ?: throw UnknownHostException("No answer scripted")
        return answer()
    }

    companion object {
        const val EPUB = "application/epub+zip"
        const val ATOM = "application/atom+xml; charset=UTF-8"

        fun ok(
            body: ByteArray,
            contentType: String?,
            contentLength: Long = body.size.toLong(),
        ) = HttpResponse(200, contentType, contentLength, null, ByteArrayInputStream(body))

        fun ok(
            body: InputStream,
            contentType: String?,
            contentLength: Long,
        ) = HttpResponse(200, contentType, contentLength, null, body)

        fun redirect(
            location: String?,
            code: Int = 302,
        ) = HttpResponse(code, "text/html", 0, location, ByteArrayInputStream(ByteArray(0)))

        fun status(code: Int) = HttpResponse(code, "text/html", 0, null, ByteArrayInputStream(ByteArray(0)))

        /** A body that sends [bytes] and then breaks, as a dropped connection does. */
        fun breakingBody(bytes: Int): InputStream =
            object : InputStream() {
                private var sent = 0

                override fun read(): Int {
                    if (sent >= bytes) throw IOException("Connection reset")
                    sent++
                    return 'x'.code
                }
            }

        /** A body that counts how much was read from it. */
        class CountingBody(
            private val size: Int,
        ) : InputStream() {
            var read = 0
                private set

            override fun read(): Int =
                if (read >= size) {
                    -1
                } else {
                    read++
                    'x'.code
                }
        }
    }
}
