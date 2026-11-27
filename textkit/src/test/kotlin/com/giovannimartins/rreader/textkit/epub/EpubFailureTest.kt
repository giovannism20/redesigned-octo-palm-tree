package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EpubFailureTest {
    @TempDir
    lateinit var directory: File

    private val book = EpubFixture().apply { document("c1.xhtml", "<p>${"Texto do capítulo. ".repeat(20)}</p>") }

    @Test
    fun `reports a file that is not a ZIP archive`() {
        val file = File(directory, "livro.epub").apply { writeText("%PDF-1.7 isto não é um ZIP") }

        assertThat(parseError(file)).isEqualTo(EpubError.NotAZipArchive)
    }

    @Test
    fun `reports a ZIP archive without a container`() {
        book.remove("META-INF/container.xml")

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.MissingContainer)
    }

    @Test
    fun `reports a container that names no package document`() {
        val container = "<container><rootfiles><rootfile media-type=\"x\"/></rootfiles></container>"
        book.entry("META-INF/container.xml", container)

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.MalformedDocument("META-INF/container.xml"))
    }

    @Test
    fun `reports a package document missing from the archive`() {
        book.remove(book.packagePath)

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.MissingPackageDocument)
    }

    @Test
    fun `reports a package document without a spine`() {
        book.entry(book.packagePath, "<package><metadata/><manifest/></package>")

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.MalformedDocument("OEBPS/content.opf"))
    }

    @Test
    fun `reports a damaged content document`() {
        val file = book.writeTo(file())
        corruptEntryData(file, "OEBPS/c1.xhtml")

        assertThat(parseError(file)).isEqualTo(EpubError.MalformedDocument("OEBPS/c1.xhtml"))
    }

    @Test
    fun `detects DRM from a rights file`() {
        book.entry("META-INF/rights.xml", "<rights/>")

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.DrmProtected)
    }

    @Test
    fun `detects DRM from encrypted content listed in the encryption file`() {
        val aes = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"
        book.entry("META-INF/encryption.xml", encryption(FONT_OBFUSCATION_IDPF, aes))

        assertThat(parseError(book.writeTo(file()))).isEqualTo(EpubError.DrmProtected)
    }

    @Test
    fun `opens books whose encryption file only obfuscates fonts`() {
        book.entry("META-INF/encryption.xml", encryption(FONT_OBFUSCATION_IDPF, FONT_OBFUSCATION_ADOBE))

        assertThat(parseBook(book.writeTo(file())).chapters).hasSize(1)
    }

    @Test
    fun `reports books without readable text`() {
        val imagesOnly =
            EpubFixture().apply {
                document("capa.xhtml", "<div><img src=\"capa.jpg\" alt=\"Capa\"/></div>", tocTitle = "Capa")
                document("vazio.xhtml", "<p> &nbsp; &#8203; </p><script>falar()</script>")
            }
        val emptySpine = EpubFixture()

        assertThat(parseError(imagesOnly.writeTo(file("imagens.epub")))).isEqualTo(EpubError.NoReadableText)
        assertThat(parseError(emptySpine.writeTo(file("vazio.epub")))).isEqualTo(EpubError.NoReadableText)
    }

    private fun file(name: String = "livro.epub"): File = File(directory, name)

    private fun encryption(vararg algorithms: String): String =
        "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" " +
            "xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\">" +
            algorithms.withIndex().joinToString("") { (index, algorithm) ->
                "<enc:EncryptedData><enc:EncryptionMethod Algorithm=\"$algorithm\"/><enc:CipherData>" +
                    "<enc:CipherReference URI=\"OEBPS/recurso$index\"/></enc:CipherData></enc:EncryptedData>"
            } +
            "</encryption>"

    private companion object {
        const val FONT_OBFUSCATION_IDPF = "http://www.idpf.org/2008/embedding"
        const val FONT_OBFUSCATION_ADOBE = "http://ns.adobe.com/pdf/enc#RC"
    }
}
