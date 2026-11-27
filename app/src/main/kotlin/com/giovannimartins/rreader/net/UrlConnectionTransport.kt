package com.giovannimartins.rreader.net

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * [HttpTransport] on the platform's `HttpsURLConnection`, so the app needs no HTTP library. Redirects are left to
 * [HttpsClient], no response is cached, and connecting and each read time out. The app installs no
 * `CookieHandler`, so cookies are neither stored nor sent. Only HTTPS is spoken: anything else is refused here, and
 * the network security config forbids cleartext and trusts no host outside the allowlists (SR-5).
 */
class UrlConnectionTransport
    @Inject
    constructor() : HttpTransport {
        override fun get(
            address: URI,
            headers: Map<String, String>,
        ): HttpResponse {
            val connection =
                address.toURL().openConnection() as? HttpsURLConnection ?: throw IOException("Only HTTPS is allowed")
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            headers.forEach(connection::setRequestProperty)
            try {
                val code = connection.responseCode
                val body =
                    if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                        connection.inputStream
                    } else {
                        connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
                    }
                return HttpResponse(
                    code = code,
                    contentType = connection.contentType,
                    contentLength = connection.contentLengthLong,
                    location = connection.getHeaderField("Location"),
                    body = body,
                    contentRange = connection.getHeaderField("Content-Range"),
                    release = connection::disconnect,
                )
            } catch (e: IOException) {
                connection.disconnect()
                throw e
            }
        }

        private companion object {
            const val CONNECT_TIMEOUT_MILLIS = 15_000
            const val READ_TIMEOUT_MILLIS = 30_000
        }
    }
