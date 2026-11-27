package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.BuildConfig
import com.giovannimartins.rreader.catalog.CatalogUserAgent
import com.giovannimartins.rreader.di.IoDispatcher
import com.giovannimartins.rreader.net.HostAllowlist
import com.giovannimartins.rreader.net.HttpTransport
import com.giovannimartins.rreader.net.HttpsClient
import com.giovannimartins.rreader.net.isPlainHttps
import com.giovannimartins.rreader.net.lowercaseHost
import kotlinx.coroutines.CoroutineDispatcher
import java.net.URI
import javax.inject.Inject

/**
 * The hosts voice packages are downloaded from: GitHub, where the sherpa-onnx releases live, and the hosts its release
 * downloads redirect to. Exact names only, no subdomains, over HTTPS on the default port. Checked on 2026-11-27:
 * `github.com/.../releases/download/...` answers 302 to `release-assets.githubusercontent.com`; GitHub served release
 * assets from `objects.githubusercontent.com` before, so that host is kept for its older links. The network security
 * config trusts the system's certificate authorities for exactly these hosts (SR-5).
 */
object VoiceModelHosts : HostAllowlist {
    val HOSTS: Set<String> =
        setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    override fun isAllowed(address: URI): Boolean = address.lowercaseHost() in HOSTS && address.isPlainHttps()
}

/**
 * The client of voice package downloads: [VoiceModelHosts] only, with a user agent that names just the app and its
 * version. The archives' integrity does not rest on it: each one is checked against the SHA-256 pinned in
 * [NeuralVoices] before use (SR-6).
 */
class VoiceModelHttp
    @Inject
    constructor(
        transport: HttpTransport,
        @IoDispatcher io: CoroutineDispatcher,
    ) : HttpsClient(transport, VoiceModelHosts, CatalogUserAgent.of(BuildConfig.VERSION_NAME, contact = ""), io)
