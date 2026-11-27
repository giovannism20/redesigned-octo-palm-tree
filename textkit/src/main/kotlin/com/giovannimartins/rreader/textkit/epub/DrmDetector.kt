package com.giovannimartins.rreader.textkit.epub

/**
 * Detects DRM-protected books. `META-INF/rights.xml` holds DRM licences; `META-INF/encryption.xml` lists encrypted
 * resources, and anything other than font obfuscation there means encrypted content. Obfuscated fonts alone do not
 * matter for reading aloud, so they do not block a book.
 */
internal object DrmDetector {
    private const val RIGHTS = "META-INF/rights.xml"
    private const val ENCRYPTION = "META-INF/encryption.xml"
    private val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

    fun isProtected(archive: EpubArchive): Boolean =
        archive.contains(RIGHTS) || archive.read(ENCRYPTION)?.let(::encryptsContent) == true

    private fun encryptsContent(encryption: ByteArray): Boolean =
        SafeXml
            .parse(encryption, ENCRYPTION)
            .elementsNamed("encryptionmethod")
            .any { it.attr("Algorithm").trim() !in FONT_OBFUSCATION }
}
