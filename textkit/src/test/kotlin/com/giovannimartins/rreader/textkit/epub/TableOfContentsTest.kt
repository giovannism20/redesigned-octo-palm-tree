package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TableOfContentsTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `reads the fragment and the depth of each link of the navigation document`() {
        val fixture =
            EpubFixture().apply {
                document("c1.xhtml", "<p>Um.</p>")
                tableOfContents(
                    navItem(
                        "Parte I",
                        "c1.xhtml",
                        navItem("Capítulo 1", "c1.xhtml#cap%C3%ADtulo-1", navItem("1", "c1.xhtml#c1-1")),
                    ),
                    navItem("Fora do livro", "https://exemplo.invalid/c1.xhtml#c1"),
                    navItem("Mesmo documento", "#toc"),
                )
            }

        val entries = read(fixture, EpubFixture.NAV_FILE, TableOfContents::fromNavigationDocument)

        assertThat(entries)
            .containsExactly(
                TocEntry("Parte I", "OEBPS/c1.xhtml", fragment = null, depth = 1),
                TocEntry("Capítulo 1", "OEBPS/c1.xhtml", fragment = "capítulo-1", depth = 2),
                TocEntry("1", "OEBPS/c1.xhtml", fragment = "c1-1", depth = 3),
            ).inOrder()
    }

    @Test
    fun `reads the fragment and the depth of each NCX point`() {
        val fixture = EpubFixture(epub3 = false).apply { document("c1.xhtml", "<p>Um.</p>") }
        val part =
            navPoint(
                "Parte I",
                "c1.xhtml#",
                navPoint("Capítulo 1", "c1.xhtml#c1"),
                navPoint("Capítulo 2", "c1.xhtml#c2"),
            )
        fixture.entry(fixture.pathOf(EpubFixture.NCX_FILE), ncx(part, navPoint("Notas", "c1.xhtml#notas")))

        val entries = read(fixture, EpubFixture.NCX_FILE, TableOfContents::fromNcx)

        assertThat(entries.map { Triple(it.title, it.fragment, it.depth) })
            .containsExactly(
                Triple("Parte I", null, 1),
                Triple("Capítulo 1", "c1", 2),
                Triple("Capítulo 2", "c2", 2),
                Triple("Notas", "notas", 1),
            ).inOrder()
    }

    /** Writes [fixture] and reads its table of contents, stored as [fileName], with [reader]. */
    private fun read(
        fixture: EpubFixture,
        fileName: String,
        reader: (EpubArchive, String) -> List<TocEntry>,
    ): List<TocEntry> =
        EpubArchive.open(fixture.writeTo(File(directory, "livro.epub")), ArchiveLimits()).use { archive ->
            reader(archive, fixture.pathOf(fileName))
        }
}
