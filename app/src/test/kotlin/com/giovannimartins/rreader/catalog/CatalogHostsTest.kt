package com.giovannimartins.rreader.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

/** SR-5: the hosts the catalogue may contact (the network security config is checked by NetworkSecurityConfigTest). */
@RunWith(RobolectricTestRunner::class)
class CatalogHostsTest {
    @Test
    fun `allows exactly Project Gutenberg's site and its official mirror over HTTPS`() {
        listOf(
            "https://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt",
            "HTTPS://WWW.GUTENBERG.ORG/ebooks/55752.opds",
            "https://www.gutenberg.org:443/ebooks/55752.opds",
            "https://gutenberg.pglaf.org/cache/epub/55752/pg55752-images-3.epub",
        ).forEach { address -> assertThat(CatalogHosts.check(address)).isNotNull() }
    }

    @Test
    fun `refuses the other hosts of Gutenberg's domains, which the app does not use`() {
        listOf(
            "https://gutenberg.org/ebooks/55752.epub3.images",
            "https://aleph.gutenberg.org/cache/epub/55752/pg55752-images-3.epub",
            "https://dev.gutenberg.org/ebooks/55752.opds",
            "https://pglaf.org/cache/epub/55752/pg55752-images-3.epub",
            "https://www.gutenberg.pglaf.org/cache/epub/55752/pg55752-images-3.epub",
            "https://gutenberg.pglaf.org.evil.example/livro.epub",
        ).forEach { address -> assertThat(CatalogHosts.check(address)).isNull() }
    }

    @Test
    fun `refuses other hosts, cleartext, other ports, user information and malformed addresses`() {
        listOf(
            "http://www.gutenberg.org/ebooks/55752.epub3.images",
            "https://evilgutenberg.org/livro.epub",
            "https://gutenberg.org.evil.example/livro.epub",
            "https://www.gutenberg.org.evil.example/livro.epub",
            "https://usuario@www.gutenberg.org/ebooks/55752.opds",
            "https://www.gutenberg.org:8443/ebooks/55752.opds",
            "ftp://www.gutenberg.org/ebooks/55752.opds",
            "file:///data/data/livro.epub",
            "/ebooks/55752.opds",
            "https://",
            "not an address",
            "https://www.gutenberg.org\\@evil.example/",
            "https://93.184.216.34/livro.epub",
        ).forEach { address -> assertThat(CatalogHosts.check(address)).isNull() }
    }

    @Test
    fun `resolves links and redirects, and refuses those that leave the allowlist`() {
        val base = URI("https://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt")

        assertThat(CatalogHosts.resolve(base, "/ebooks/search.opds/?query=machado+l.pt&start_index=26").toString())
            .isEqualTo("https://www.gutenberg.org/ebooks/search.opds/?query=machado+l.pt&start_index=26")
        assertThat(CatalogHosts.resolve(base, "https://gutenberg.pglaf.org/cache/epub/1/pg1-images-3.epub")).isNotNull()
        assertThat(CatalogHosts.resolve(base, "https://aleph.gutenberg.org/cache/epub/1/pg1.epub")).isNull()
        assertThat(CatalogHosts.resolve(base, "//evil.example/livro.epub")).isNull()
        assertThat(CatalogHosts.resolve(base, "https://evil.example/livro.epub")).isNull()
        assertThat(CatalogHosts.resolve(base, "http://www.gutenberg.org/cache/epub/1/pg1.epub")).isNull()
    }
}
