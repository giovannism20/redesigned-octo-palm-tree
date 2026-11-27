package com.giovannimartins.rreader.net

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** The answer to one GET: its status, the headers the app needs and the body, which must be closed. */
class HttpResponse(
    val code: Int,
    /** The `Content-Type` header, or null. */
    val contentType: String?,
    /** The `Content-Length` header, or -1 when the server sent none. */
    val contentLength: Long,
    /** The `Location` header of a redirect, or null. */
    val location: String?,
    val body: InputStream,
    /** The `Content-Range` header of a partial answer (HTTP 206), or null. */
    val contentRange: String? = null,
    private val release: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            release()
        }
    }
}

/**
 * Sends one HTTPS GET and does not follow redirects: [HttpsClient] decides where to go next. The app uses
 * [UrlConnectionTransport]; tests use a fake.
 */
fun interface HttpTransport {
    /** Sends a GET for [address] with [headers]; blocks, and throws [IOException] when the network fails. */
    @Throws(IOException::class)
    fun get(
        address: URI,
        headers: Map<String, String>,
    ): HttpResponse
}
