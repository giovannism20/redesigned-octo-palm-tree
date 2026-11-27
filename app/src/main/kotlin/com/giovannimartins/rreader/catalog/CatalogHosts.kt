package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.Diagnostics
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * The hosts the app may contact, the code half of SR-5: Project Gutenberg's site, [DOMAIN] and its subdomains, over
 * HTTPS on the default port, without user information in the address. The other half is the network security config
 * (`res/xml/network_security_config.xml`), which forbids cleartext and trusts certificates for [DOMAIN] only, so a
 * connection to any other host also fails at the TLS handshake (ADR 0017).
 */
object CatalogHosts {
    /** The allowlisted domain; its subdomains, such as `www.gutenberg.org`, are allowed too. */
    const val DOMAIN: String = "gutenberg.org"

    private const val HTTPS_PORT = 443
    private const val DEFAULT_PORT = -1

    /** [address] when the app may contact it, else null. */
    fun check(address: String): URI? = parse(address)?.takeIf(::isAllowed)

    /**
     * Where [reference] leads from [base], as a redirect's `Location` or a feed's link gives it (often relative), when
     * the app may contact it, else null.
     */
    fun resolve(
        base: URI,
        reference: String,
    ): URI? = parse(reference)?.let(base::resolve)?.takeIf(::isAllowed)

    /** Whether the app may contact [address]. */
    fun isAllowed(address: URI): Boolean {
        val host = address.host?.lowercase(Locale.ROOT)
        val known = host != null && (host == DOMAIN || host.endsWith(".$DOMAIN"))
        val secure = address.scheme.equals("https", ignoreCase = true) && address.rawUserInfo == null
        val defaultPort = address.port == DEFAULT_PORT || address.port == HTTPS_PORT
        return known && secure && defaultPort
    }

    private fun parse(text: String): URI? =
        try {
            URI(text.trim())
        } catch (e: URISyntaxException) {
            // Only the exception type is recorded: addresses can hold search terms (SR-8).
            Diagnostics.recordHandled("read a catalogue address", e)
            null
        }
}
