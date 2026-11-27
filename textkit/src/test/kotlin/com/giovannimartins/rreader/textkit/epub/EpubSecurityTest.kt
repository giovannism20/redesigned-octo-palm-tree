package com.giovannimartins.rreader.textkit.epub

import com.giovannimartins.rreader.textkit.model.Chapter
import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Hostile EPUBs for security requirements SR-1 to SR-3 (see `docs/security.md`). */
class EpubSecurityTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `SR-1 references that climb above the archive root are treated as missing`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Texto do livro.</p>", tocTitle = "Capítulo 1")
                extraManifest += manifestItem("fora", "../../fora.xhtml", "application/xhtml+xml")
                extraManifest += manifestItem("cod", "%2E%2E/%2e%2e/fora.xhtml", "application/xhtml+xml")
                extraManifest += manifestItem("url", "file:///etc/passwd", "application/xhtml+xml")
                extraManifest += manifestItem("capa", "../../capa.jpg", "image/jpeg", properties = "cover-image")
                extraSpine += listOf("fora", "cod", "url").map { "<itemref idref=\"$it\"/>" }
                entry("../../fora.xhtml", xhtml("<p>CONTEÚDO DE FORA</p>"))
                // A resolver that clamped ".." at the root instead of rejecting it would find this entry.
                entry("fora.xhtml", xhtml("<p>CONTEÚDO DE FORA</p>"))
                entry("../../capa.jpg", byteArrayOf(1, 2, 3))
            }
        val file = fixture.writeTo(File(directory, "livro.epub"))
        val filesBefore = directory.walk().toSet()

        val book = parseBook(file)

        assertThat(book.chapters).containsExactly(Chapter("Capítulo 1", "Texto do livro."))
        assertThat(book.cover).isNull()
        assertThat(directory.walk().toSet()).isEqualTo(filesBefore)
    }

    @Test
    fun `SR-1 a package document outside the archive root counts as missing`() {
        val fixture = EpubFixture().apply { document("c1.xhtml", "<p>Texto.</p>") }
        val container =
            "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>" +
                "<rootfile full-path=\"../content.opf\" media-type=\"application/oebps-package+xml\"/>" +
                "</rootfiles></container>"
        fixture.entry("META-INF/container.xml", container)
        fixture.entry("../content.opf", "<package><manifest/><spine/></package>")

        val file = fixture.writeTo(File(directory, "livro.epub"))

        assertThat(parseError(file)).isEqualTo(EpubError.MissingPackageDocument)
    }

    @Test
    fun `SR-2 an archive with more entries than allowed is rejected`() {
        // mimetype, container, package, navigation and two content documents: six entries.
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Um.</p>")
                document("c2.xhtml", "<p>Dois.</p>")
            }
        val file = fixture.writeTo(File(directory, "livro.epub"))

        assertThat(parseError(file, EpubParser(ArchiveLimits(maxEntries = 5))))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRIES))
        assertThat(parseBook(file, EpubParser(ArchiveLimits(maxEntries = 6))).chapters).hasSize(2)
    }

    @Test
    fun `SR-2 an entry larger than the per-entry limit is rejected`() {
        val fixture = EpubFixture().apply { document("c1.xhtml", "<p>${"palavra ".repeat(1_000)}</p>") }
        val parser = EpubParser(ArchiveLimits(maxEntryBytes = 4_096))

        assertThat(parseError(fixture.writeTo(File(directory, "livro.epub")), parser))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
    }

    @Test
    fun `SR-2 a zip bomb that declares a tiny size is stopped while inflating`() {
        val fixture = EpubFixture().apply { document("bomba.xhtml", "<p>${" ".repeat(8_000_000)}</p>") }
        val file = fixture.writeTo(File(directory, "bomba.epub"))
        declareUncompressedSize(file, "OEBPS/bomba.xhtml", 64)
        assertThat(file.length()).isLessThan(100_000)

        assertThat(parseError(file, EpubParser(ArchiveLimits(maxEntryBytes = 1_048_576))))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
    }

    @Test
    fun `SR-2 reading stops when the bytes read across entries exceed the total limit`() {
        val fixture = EpubFixture().apply { repeat(10) { document("c$it.xhtml", "<p>${"texto ".repeat(300)}</p>") } }
        val limits = ArchiveLimits(maxEntryBytes = 4_096, maxTotalBytes = 10_240)

        assertThat(parseError(fixture.writeTo(File(directory, "livro.epub")), EpubParser(limits)))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_TOTAL_BYTES))
    }

    @Test
    fun `SR-2 the cover image is read within the same limits`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Texto.</p>")
                extraManifest += manifestItem("capa", "capa.jpg", "image/jpeg", properties = "cover-image")
                entry(pathOf("capa.jpg"), ByteArray(8_192) { it.toByte() })
            }
        val parser = EpubParser(ArchiveLimits(maxEntryBytes = 4_096))

        assertThat(parseError(fixture.writeTo(File(directory, "livro.epub")), parser))
            .isEqualTo(EpubError.LimitExceeded(ArchiveLimit.MAX_ENTRY_BYTES))
    }

    @Test
    fun `SR-13 a huge title, author list, language and description are cut to their limits`() {
        val fixture =
            EpubFixture().apply {
                title = "Título ".repeat(500_000)
                creators.clear()
                creators += List(1_000) { "Autor $it ${"de Tal ".repeat(100)}" }
                language = "pt-${"BR-".repeat(100_000)}x"
                description = "Descrição sem fim. ".repeat(200_000)
                document("c1.xhtml", "<p>Texto do livro.</p>", tocTitle = "Capítulo ".repeat(200_000))
            }

        val book = parseBook(fixture.writeTo(File(directory, "livro.epub")))

        val metadata = book.metadata
        assertThat(metadata.title).hasLength(MetadataLimits.MAX_TITLE_LENGTH)
        assertThat(metadata.title).startsWith("Título Título")
        assertThat(metadata.title).endsWith("…")
        assertThat(metadata.authors).hasSize(MetadataLimits.MAX_AUTHORS)
        assertThat(metadata.authors.first()).startsWith("Autor 0 de Tal")
        metadata.authors.forEach { assertThat(it.length).isAtMost(MetadataLimits.MAX_AUTHOR_LENGTH) }
        assertThat(metadata.language).isNull()
        assertThat(metadata.description!!.length).isAtMost(MetadataLimits.MAX_DESCRIPTION_LENGTH)
        assertThat(
            book.chapters
                .single()
                .title!!
                .length,
        ).isAtMost(MetadataLimits.MAX_TITLE_LENGTH)
    }

    @Test
    fun `SR-3 content documents never load external entities or DTDs`() {
        val secret = File(directory, "segredo.txt").apply { writeText("SEGREDO-DO-DISCO") }
        val dtd = File(directory, "externo.dtd").apply { writeText("<!ENTITY externo \"SEGREDO-DO-DTD\">") }
        val document =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE html SYSTEM \"${dtd.toURI()}\" [<!ENTITY xxe SYSTEM \"${secret.toURI()}\">]>\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                "<body><p>Início &xxe; meio &externo; fim.</p></body></html>"
        val fixture = EpubFixture().apply { document("c1.xhtml", "", tocTitle = "Capítulo 1") }
        fixture.entry(fixture.pathOf("c1.xhtml"), document)

        val text = parseBook(fixture.writeTo(File(directory, "livro.epub"))).chapters.single().text

        assertThat(text).doesNotContain("SEGREDO")
        assertThat(text).isEqualTo("Início &xxe; meio &externo; fim.")
    }

    @Test
    fun `SR-3 the package document neither loads nor expands declared entities`() {
        val secret = File(directory, "segredo.txt").apply { writeText("SEGREDO-DO-DISCO") }
        val laughs = (2..6).joinToString("") { level -> "<!ENTITY lol$level \"${"&lol${level - 1};".repeat(10)}\">" }
        val fixture =
            EpubFixture().apply {
                title = "&xxe;"
                description = "&lol6;"
                document("c1.xhtml", "<p>Texto.</p>")
            }
        val doctype = "<!DOCTYPE package [<!ENTITY xxe SYSTEM \"${secret.toURI()}\"><!ENTITY lol1 \"lol\">$laughs]>"
        fixture.entry(fixture.packagePath, fixture.packageXml().replaceFirst("?>", "?>$doctype"))

        val metadata = parseBook(fixture.writeTo(File(directory, "livro.epub"))).metadata

        assertThat(metadata.title).isEqualTo("&xxe;")
        assertThat(metadata.description).isEqualTo("&lol6;")
    }
}
