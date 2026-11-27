package com.giovannimartins.rreader.textkit.epub

/** The OCF container file, which names the package document. */
internal object OcfContainer {
    const val PATH = "META-INF/container.xml"
    private const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"

    /**
     * The entry name of the package document: the first `rootfile` with the package media type, else the first
     * `rootfile` with a `full-path`. A path that leaves the archive root counts as a missing package document.
     */
    fun packagePath(archive: EpubArchive): String {
        val bytes = archive.read(PATH) ?: abortParse(EpubError.MissingContainer)
        val rootfiles =
            SafeXml.parse(bytes, PATH).elementsNamed("rootfile").filter { it.attr("full-path").isNotBlank() }
        val rootfile =
            rootfiles.firstOrNull { it.attr("media-type").trim() == PACKAGE_MEDIA_TYPE }
                ?: rootfiles.firstOrNull()
                ?: abortParse(EpubError.MalformedDocument(PATH))
        return ArchivePaths.resolve("", rootfile.attr("full-path")) ?: abortParse(EpubError.MissingPackageDocument)
    }
}
