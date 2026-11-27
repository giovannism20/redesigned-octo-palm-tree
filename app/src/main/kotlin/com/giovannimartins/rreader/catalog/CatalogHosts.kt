package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.net.HostAllowlist
import com.giovannimartins.rreader.net.isPlainHttps
import com.giovannimartins.rreader.net.lowercaseHost
import java.net.URI

/**
 * The hosts the catalogue may contact: exactly Project Gutenberg's site, [SITE], and its official mirror, [MIRROR],
 * over HTTPS on the default port, without user information in the address. The network security config trusts
 * certificates for these two hosts (ADR 0017, ADR 0019), so a connection to any other host also fails at the TLS
 * handshake (see [HostAllowlist]).
 */
object CatalogHosts : HostAllowlist {
    /** Project Gutenberg's site: searches, and the first place a book is downloaded from. */
    const val SITE: String = "www.gutenberg.org"

    /** Project Gutenberg's official mirror, run by its foundation: books come from it when the site is down. */
    const val MIRROR: String = "gutenberg.pglaf.org"

    /** Every host the catalogue may contact; exact names, without subdomains. */
    val HOSTS: Set<String> = setOf(SITE, MIRROR)

    override fun isAllowed(address: URI): Boolean = address.lowercaseHost() in HOSTS && address.isPlainHttps()
}
