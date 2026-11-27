package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.di.IoDispatcher
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
import javax.inject.Inject

/** One GET the catalogue makes, and what its answer must be to be accepted. */
data class CatalogRequest(
    /** An absolute HTTPS address on an allowlisted host ([CatalogHosts]). */
    val address: String,
    /** The accepted media types, in lower case and without parameters, such as `application/epub+zip`. */
    val mediaTypes: Set<String>,
    /** The largest body accepted, in bytes. */
    val maxBytes: Long,
)

/** Why a catalogue request failed. [retryable] failures may work later. */
enum class FetchFailure(
    val retryable: Boolean,
) {
    /** No connection, a timeout, or a connection that broke while the answer arrived. */
    NETWORK(true),

    /** The server is busy or failing (HTTP 429 or 5xx). */
    SERVER(true),

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

/** Outcome of [CatalogHttp.fetch]. */
sealed interface FetchResult {
    /** The whole body, [bytes] long, was written to the sink. */
    data class Received(
        val bytes: Long,
    ) : FetchResult

    data class Failed(
        val failure: FetchFailure,
    ) : FetchResult
}

/**
 * The app's only way to the network (SR-5): HTTPS GETs to Project Gutenberg, made because the user searched the
 * catalogue or asked for a book.
 *
 * Every address, and every redirect target (at most [MAX_REDIRECTS] of them), must pass [CatalogHosts], or the request
 * ends as [FetchFailure.BLOCKED] without contacting it. Only a 200 answer of an expected media type is read, and
 * reading stops as soon as the body is larger than allowed, whether announced or counted. A body shorter than
 * announced is a broken connection. Requests carry only the [CatalogUserAgent] and the accepted types: no cookies, no
 * identifiers. Addresses can hold search terms, so they are never logged: failures are recorded by type only (SR-8).
 */
class CatalogHttp
    @Inject
    constructor(
        private val transport: HttpTransport,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /**
         * Fetches [request] into [sink], telling [onProgress] the bytes received so far and the announced length (-1
         * when unknown). Network problems are failures; only a [sink] that cannot be written throws an [IOException].
         */
        suspend fun fetch(
            request: CatalogRequest,
            sink: OutputStream,
            onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
        ): FetchResult =
            withContext(io) {
                var step: Step = Step.Visit(CatalogHosts.check(request.address))
                repeat(MAX_REDIRECTS + 1) {
                    val next = step as? Step.Visit ?: return@repeat
                    step = next.address?.let { visit(it, request, sink, onProgress) } ?: Step.Done(BLOCKED)
                }
                (step as? Step.Done)?.result ?: BLOCKED
            }

        private suspend fun visit(
            address: URI,
            request: CatalogRequest,
            sink: OutputStream,
            onProgress: (Long, Long) -> Unit,
        ): Step {
            val response =
                try {
                    transport.get(address, headers(request))
                } catch (e: IOException) {
                    Diagnostics.recordHandled("catalogue request", e)
                    null
                }
            return response?.use { answer(it, address, request, sink, onProgress) } ?: Step.Done(NETWORK)
        }

        private suspend fun answer(
            response: HttpResponse,
            address: URI,
            request: CatalogRequest,
            sink: OutputStream,
            onProgress: (Long, Long) -> Unit,
        ): Step =
            when (response.code) {
                in REDIRECTS -> Step.Visit(response.location?.let { CatalogHosts.resolve(address, it) })
                HttpURLConnection.HTTP_OK -> Step.Done(receive(response, request, sink, onProgress))
                else -> Step.Done(FetchResult.Failed(failureOf(response.code)))
            }

        private suspend fun receive(
            response: HttpResponse,
            request: CatalogRequest,
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
                response.contentLength > request.maxBytes -> FetchResult.Failed(FetchFailure.TOO_LARGE)
                else -> copy(response, request.maxBytes, sink, onProgress)
            }
        }

        private suspend fun copy(
            response: HttpResponse,
            maxBytes: Long,
            sink: OutputStream,
            onProgress: (Long, Long) -> Unit,
        ): FetchResult {
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            var read = readChunk(response.body, buffer)
            while (read > 0 && total + read <= maxBytes) {
                sink.write(buffer, 0, read)
                total += read
                onProgress(total, response.contentLength)
                currentCoroutineContext().ensureActive()
                read = readChunk(response.body, buffer)
            }
            val truncated = response.contentLength >= 0 && total != response.contentLength
            return when {
                read == READ_FAILED || (read < 0 && truncated) -> NETWORK
                read > 0 -> FetchResult.Failed(FetchFailure.TOO_LARGE)
                else -> FetchResult.Received(total)
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
                Diagnostics.recordHandled("catalogue answer", e)
                READ_FAILED
            }

        private fun headers(request: CatalogRequest): Map<String, String> =
            mapOf("User-Agent" to CatalogUserAgent.value, "Accept" to request.mediaTypes.joinToString(", "))

        private fun failureOf(code: Int): FetchFailure =
            when {
                code == HTTP_NOT_FOUND || code == HTTP_GONE -> FetchFailure.NOT_FOUND
                code == TOO_MANY_REQUESTS || code >= HTTP_INTERNAL_ERROR -> FetchFailure.SERVER
                else -> FetchFailure.REFUSED
            }

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
            private val BLOCKED = FetchResult.Failed(FetchFailure.BLOCKED)
            private val NETWORK = FetchResult.Failed(FetchFailure.NETWORK)
        }
    }
