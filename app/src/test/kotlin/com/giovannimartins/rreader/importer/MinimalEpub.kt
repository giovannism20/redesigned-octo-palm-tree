package com.giovannimartins.rreader.importer

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a small, valid EPUB 3 in memory for import tests. */
internal object MinimalEpub {
    private val DEFAULT_CHAPTERS =
        listOf("I" to "Verdes mares bravios de minha terra natal.", "II" to "Onde vai a afouta jangada?")

    fun bytes(
        title: String = "Iracema",
        author: String = "José de Alencar",
        chapters: List<Pair<String, String>> = DEFAULT_CHAPTERS,
    ): ByteArray {
        val entries =
            listOf(
                "META-INF/container.xml" to CONTAINER,
                "OEBPS/content.opf" to packageDocument(title, author, chapters.size),
                "OEBPS/nav.xhtml" to navDocument(chapters.map { it.first }),
            ) + chapters.mapIndexed { i, (heading, text) -> "OEBPS/c$i.xhtml" to chapterDocument(heading, text) }
        return zip(entries)
    }

    private fun packageDocument(
        title: String,
        author: String,
        chapterCount: Int,
    ): String {
        val manifest =
            (0 until chapterCount).joinToString("\n") { i ->
                """<item id="c$i" href="c$i.xhtml" media-type="application/xhtml+xml"/>"""
            }
        val spine = (0 until chapterCount).joinToString("\n") { i -> """<itemref idref="c$i"/>""" }
        return """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">test</dc:identifier>
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
                <dc:language>pt-BR</dc:language>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                $manifest
              </manifest>
              <spine>$spine</spine>
            </package>
            """.trimIndent()
    }

    private fun navDocument(titles: List<String>): String {
        val items =
            titles.withIndex().joinToString(
                "\n",
            ) { (i, title) -> """<li><a href="c$i.xhtml">$title</a></li>""" }
        return """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body><nav epub:type="toc"><ol>$items</ol></nav></body>
            </html>
            """.trimIndent()
    }

    private fun chapterDocument(
        heading: String,
        text: String,
    ): String = """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>$heading</h1><p>$text</p></body></html>"""

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mimetype = "application/epub+zip".toByteArray()
            val stored =
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            zip.putNextEntry(stored)
            zip.write(mimetype)
            zip.closeEntry()
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private const val CONTAINER =
        """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
}
