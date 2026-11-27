package com.giovannimartins.rreader.net

import com.giovannimartins.rreader.Diagnostics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_GONE
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.URI
import java.util.Locale

/** One GET, and what its answer must be to be accepted. */
data class HttpsRequest(
    /** An absolute HTTPS address on an allowlisted host. */
    val address: String,
    /** The accepted media types, in lower case and without parameters, such as `application/epub+zip`. */
    val mediaTypes: Set<String>,
    /** The largest body accepted, in bytes; for a resumed download, the size of the whole file. */
    val maxBytes: Long,
    /**
     * The bytes of the file received by an earlier, interrupted request. When positive, only the rest is asked for
     * (`Range`) and the answer must be exactly that rest (HTTP 206 from this offset to the end of the file).
     */
    val resumeFrom: Long = 0,
)

/** Why a request failed. [retryable] failures may work later. */
enum class FetchFailure(
    val retryable: Boolean,
) {
    /** No connection, a timeout, or a connection that broke while the answer arrived. */
    NETWORK(true),

    /** The server is busy or failing (HTTP 429 or 5xx). */
    SERVER(true),

    /** The server would not continue an interrupted download where it stopped: it must start again from the beginning. */
    RESUME_REFUSED(true),

    /** The address does not exist (HTTP 404 or 410). */
    NOT_FOUND(false),

    /** Any other answer than 200, a redirect, or those above. */
    REFUSED(false),

    /** The answer is not of an expected media type. */
    WRONG_TYPE(false),

    /** The answer is larger than allowed. */
    TOO_LARGE(false),

    /** The address, or a redirect, leads off the allowlist or away from HTTPS, or redirects too often (SR-5). */
    BLOCKED(false),
}

/** Outcome of [HttpsClient.fetch]. */
sealed interface FetchResult {
    /** The whole body was written to the sink: the file is [bytes] long, counting those of an earlier request. */
    data class Received(
        val bytes: Long,
    ) : FetchResult

    data class Failed(
        val failure: FetchFailure,
    ) : FetchResult
}

/**
 * The app's way to the network (SR-5): HTTPS GETs made because the listener asked for something, to the hosts of one
 * [HostAllowlist].
 *
 * Every address, and every redirect target (at most [MAX_REDIRECTS] of them), must pass the allowlist, or the request
 * ends as [FetchFailure.BLOCKED] without contacting it. Only a 200 answer of an expected media type is read (or, when
 * resuming, a 206 with the rest of the file), and reading stops as soon as the body is larger than allowed, whether
 * announced or counted. A body shorter than announced is a broken connection. Requests carry only the [userAgent],
 * the accepted types and, when resuming, the range: no cookies, no identifiers. Addresses can hold search terms, so
 * they are never logged: failures are recorded by type only (SR-8).
 */
open class HttpsClient(
    private val transport: HttpTransport,
    private val hosts: HostAllowlist,
    private val userAgent: String,
    private val io: CoroutineDispatcher,
) {
    /**
     * Fetches [request] into [sink], telling [onProgress] the bytes of the file received so far and its announced
     * length (-1 when unknown). Network problems are failures; only a [sink] that cannot be written throws an
     * [IOException].
     */
    suspend fun fetch(
        request: HttpsRequest,
        sink: OutputStream,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): FetchResult =
        withContext(io) {
            var step: Step = Step.Visit(hosts.check(request.address))
            repeat(MAX_REDIRECTS + 1) {
                val next = step as? Step.Visit ?: return@repeat
                step = next.address?.let { visit(it, request, sink, onProgress) } ?: Step.Done(BLOCKED)
            }
            (step as? Step.Done)?.result ?: BLOCKED
        }

    private suspend fun visit(
        address: URI,
        request: HttpsRequest,
        sink: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): Step {
        val response =
            try {
                transport.get(address, headers(request))
            } catch (e: IOException) {
                Diagnostics.recordHandled("network request", e)
                null
            }
        return response?.use { answer(it, address, request, sink, onProgress) } ?: Step.Done(NETWORK)
    }

    private suspend fun answer(
        response: HttpResponse,
        address: URI,
        request: HttpsRequest,
        sink: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): Step =
        when (response.code) {
            in REDIRECTS -> {
                Step.Visit(response.location?.let { hosts.resolve(address, it) })
            }

            HttpURLConnection.HTTP_OK -> {
                // A server that ignores the range sends the whole file again: start over rather than append it.
                val result =
                    if (request.resumeFrom > 0) {
                        RESUME_REFUSED
                    } else {
                        receive(response, request, Body(0, response.contentLength), sink, onProgress)
                    }
                Step.Done(result)
            }

            HttpURLConnection.HTTP_PARTIAL -> {
                val rest = restOf(response, request)
                val result =
                    when {
                        request.resumeFrom <= 0 -> FetchResult.Failed(FetchFailure.REFUSED)
                        rest == null -> RESUME_REFUSED
                        else -> receive(response, request, rest, sink, onProgress)
                    }
                Step.Done(result)
            }

            RANGE_NOT_SATISFIABLE -> {
                Step.Done(if (request.resumeFrom > 0) RESUME_REFUSED else FetchResult.Failed(FetchFailure.REFUSED))
            }

            else -> {
                Step.Done(FetchResult.Failed(failureOf(response.code)))
            }
        }

    /** The rest of the file in a 206 answer to a resumed request, or null when it is not exactly what was asked. */
    private fun restOf(
        response: HttpResponse,
        request: HttpsRequest,
    ): Body? =
        response.contentRange
            ?.let(CONTENT_RANGE::matchEntire)
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf { it.size == RANGE_PARTS }
            ?.let { (first, last, total) -> Range(first, last, total) }
            ?.takeIf { it.isRestFrom(request.resumeFrom, response.contentLength) }
            ?.let { Body(it.first, it.total) }

    /** `Content-Range: bytes first-last/total`. */
    private data class Range(
        val first: Long,
        val last: Long,
        val total: Long,
    ) {
        /** Whether this is the file from [offset] to its end, in a body of [length] bytes (-1 when not announced). */
        fun isRestFrom(
            offset: Long,
            length: Long,
        ): Boolean {
            val toTheEnd = first == offset && last == total - 1
            return toTheEnd && (length < 0 || length == last - first + 1)
        }
    }

    private suspend fun receive(
        response: HttpResponse,
        request: HttpsRequest,
        body: Body,
        sink: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): FetchResult {
        val mediaType =
            response.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
        return when {
            mediaType !in request.mediaTypes -> FetchResult.Failed(FetchFailure.WRONG_TYPE)
            body.total > request.maxBytes -> FetchResult.Failed(FetchFailure.TOO_LARGE)
            else -> copy(response, body, request.maxBytes, sink, onProgress)
        }
    }

    private suspend fun copy(
        response: HttpResponse,
        body: Body,
        maxBytes: Long,
        sink: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): FetchResult {
        val buffer = ByteArray(BUFFER_SIZE)
        var received = 0L
        var read = readChunk(response.body, buffer)
        while (read > 0 && body.offset + received + read <= maxBytes) {
            sink.write(buffer, 0, read)
            received += read
            onProgress(body.offset + received, body.total)
            currentCoroutineContext().ensureActive()
            read = readChunk(response.body, buffer)
        }
        val truncated = response.contentLength >= 0 && received != response.contentLength
        return when {
            read == READ_FAILED || (read < 0 && truncated) -> NETWORK
            read > 0 -> FetchResult.Failed(FetchFailure.TOO_LARGE)
            else -> FetchResult.Received(body.offset + received)
        }
    }

    /** Reads into [buffer]: the number of bytes read, -1 at the end of the body, [READ_FAILED] when it breaks. */
    private fun readChunk(
        body: InputStream,
        buffer: ByteArray,
    ): Int =
        try {
            body.read(buffer)
        } catch (e: IOException) {
            Diagnostics.recordHandled("network answer", e)
            READ_FAILED
        }

    private fun headers(request: HttpsRequest): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", request.mediaTypes.joinToString(", "))
            if (request.resumeFrom > 0) put("Range", "bytes=${request.resumeFrom}-")
        }

    private fun failureOf(code: Int): FetchFailure =
        when {
            code == HTTP_NOT_FOUND || code == HTTP_GONE -> FetchFailure.NOT_FOUND
            code == TOO_MANY_REQUESTS || code >= HTTP_INTERNAL_ERROR -> FetchFailure.SERVER
            else -> FetchFailure.REFUSED
        }

    /** Where a body starts in the file ([offset] bytes were received before) and the file's length (-1 if unknown). */
    private data class Body(
        val offset: Long,
        val total: Long,
    )

    /** Where a request stands: an address to visit next (null when it may not be visited), or its result. */
    private sealed interface Step {
        data class Visit(
            val address: URI?,
        ) : Step

        data class Done(
            val result: FetchResult,
        ) : Step
    }

    companion object {
        /** The most redirects followed for one request. */
        const val MAX_REDIRECTS: Int = 5

        private const val BUFFER_SIZE = 64 * 1024
        private const val READ_FAILED = -2
        private const val TOO_MANY_REQUESTS = 429
        private const val RANGE_NOT_SATISFIABLE = 416
        private const val RANGE_PARTS = 3
        private const val TEMPORARY_REDIRECT = 307
        private const val PERMANENT_REDIRECT = 308
        private val REDIRECTS =
            setOf(
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                TEMPORARY_REDIRECT,
                PERMANENT_REDIRECT,
            )

        /** `Content-Range: bytes <first>-<last>/<length>`, the form of a partial answer with a known length. */
        private val CONTENT_RANGE = Regex("""\s*bytes\s+(\d+)-(\d+)/(\d+)\s*""", RegexOption.IGNORE_CASE)
        private val BLOCKED = FetchResult.Failed(FetchFailure.BLOCKED)
        private val NETWORK = FetchResult.Failed(FetchFailure.NETWORK)
        private val RESUME_REFUSED = FetchResult.Failed(FetchFailure.RESUME_REFUSED)
    }
}
