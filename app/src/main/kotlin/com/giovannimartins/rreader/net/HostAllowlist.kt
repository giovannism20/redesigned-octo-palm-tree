package com.giovannimartins.rreader.net

import com.giovannimartins.rreader.Diagnostics
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * The hosts one kind of request may contact, the code half of SR-5: every address and every redirect is checked
 * before a connection is made. The other half is the network security config (`res/xml/network_security_config.xml`),
 * which forbids cleartext and trusts certificates only for the allowlisted hosts, so a connection to any other host
 * also fails at the TLS handshake.
 */
fun interface HostAllowlist {
    /** Whether the app may contact [address]. */
    fun isAllowed(address: URI): Boolean

    /** [address] when the app may contact it, else null. */
    fun check(address: String): URI? = parseAddress(address)?.takeIf(::isAllowed)

    /**
     * Where [reference] leads from [base], as a redirect's `Location` or a feed's link gives it (often relative), when
     * the app may contact it, else null.
     */
    fun resolve(
        base: URI,
        reference: String,
    ): URI? = parseAddress(reference)?.let(base::resolve)?.takeIf(::isAllowed)
}

/** Whether this is an HTTPS address on the default port, without user information: the only kind the app contacts. */
fun URI.isPlainHttps(): Boolean {
    val secure = scheme.equals("https", ignoreCase = true) && rawUserInfo == null
    return secure && (port == DEFAULT_PORT || port == HTTPS_PORT)
}

/** The host of this address in lower case, or null when it has none. */
fun URI.lowercaseHost(): String? = host?.lowercase(Locale.ROOT)

private fun parseAddress(text: String): URI? =
    try {
        URI(text.trim())
    } catch (e: URISyntaxException) {
        // Only the exception type is recorded: addresses can hold search terms (SR-8).
        Diagnostics.recordHandled("read an address", e)
        null
    }

private const val HTTPS_PORT = 443
private const val DEFAULT_PORT = -1
