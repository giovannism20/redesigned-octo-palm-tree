package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.net.HttpTransport
import com.giovannimartins.rreader.net.HttpsClient
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * The catalogue's client: HTTPS GETs to Project Gutenberg ([CatalogHosts]), made because the user searched the
 * catalogue or asked for a book, with the [CatalogUserAgent]. See [HttpsClient] for what it sends and accepts.
 */
class CatalogHttp
    @Inject
    constructor(
        transport: HttpTransport,
        @IoDispatcher io: CoroutineDispatcher,
    ) : HttpsClient(transport, CatalogHosts, CatalogUserAgent.value, io)
