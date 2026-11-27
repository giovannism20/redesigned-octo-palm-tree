package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ArchivePathsTest {
    @Test
    fun `resolves references against the directory of the referring document`() {
        assertThat(ArchivePaths.resolve("OEBPS", "Text/c1.xhtml")).isEqualTo("OEBPS/Text/c1.xhtml")
        assertThat(ArchivePaths.resolve("", "content.opf")).isEqualTo("content.opf")
        assertThat(ArchivePaths.resolve("OEBPS/Text", "../Images/cover.jpg")).isEqualTo("OEBPS/Images/cover.jpg")
        assertThat(ArchivePaths.resolve("OEBPS", "./Text//c1.xhtml")).isEqualTo("OEBPS/Text/c1.xhtml")
        assertThat(ArchivePaths.resolve("OEBPS", "/Images/cover.jpg")).isEqualTo("Images/cover.jpg")
    }

    @Test
    fun `drops fragments and queries`() {
        assertThat(ArchivePaths.resolve("OEBPS", "c1.xhtml#parte-2")).isEqualTo("OEBPS/c1.xhtml")
        assertThat(ArchivePaths.resolve("OEBPS", "c1.xhtml?v=2#x")).isEqualTo("OEBPS/c1.xhtml")
    }

    @Test
    fun `decodes percent escapes as UTF-8 and keeps malformed ones`() {
        assertThat(ArchivePaths.resolve("OEBPS", "Cap%C3%ADtulo%201.xhtml")).isEqualTo("OEBPS/Capítulo 1.xhtml")
        assertThat(ArchivePaths.resolve("", "a%zz+b%2.xhtml")).isEqualTo("a%zz+b%2.xhtml")
        assertThat(ArchivePaths.resolve("", "Capítulo.xhtml")).isEqualTo("Capítulo.xhtml")
    }

    @Test
    fun `rejects references that climb above the archive root`() {
        assertThat(ArchivePaths.resolve("OEBPS", "../../etc/passwd")).isNull()
        assertThat(ArchivePaths.resolve("", "../content.opf")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS", "%2e%2e/%2E%2E/escape.xhtml")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS/Text", "../../..%2fescape.xhtml")).isNull()
    }

    @Test
    fun `rejects absolute URLs and same-document references`() {
        assertThat(ArchivePaths.resolve("OEBPS", "https://example.org/c1.xhtml")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS", "file:///etc/passwd")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS", "data:text/plain,oi")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS", "#nota-1")).isNull()
        assertThat(ArchivePaths.resolve("OEBPS", "  ")).isNull()
    }

    @Test
    fun `finds the directory of an entry`() {
        assertThat(ArchivePaths.parentOf("OEBPS/Text/c1.xhtml")).isEqualTo("OEBPS/Text")
        assertThat(ArchivePaths.parentOf("content.opf")).isEmpty()
    }
}
