package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.model.MetadataLimits
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentInfoCleanerTest {
    @Test
    fun `keeps a real title, cleaned`() {
        assertThat(DocumentInfoCleaner.title("  Dom \n Casmurro ")).isEqualTo("Dom Casmurro")
        assertThat(DocumentInfoCleaner.title("1984")).isEqualTo("1984")
        assertThat(
            DocumentInfoCleaner.title("Memórias Póstumas de Brás Cubas"),
        ).isEqualTo("Memórias Póstumas de Brás Cubas")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "Microsoft Word - Dom Casmurro.doc",
            "Microsoft PowerPoint - aula",
            "livro.pdf",
            "Dom Casmurro.docx",
            "C:\\Users\\leitor\\Documentos\\livro.rtf",
            "Untitled",
            "Sem título",
            "Documento1",
            "Document 2",
            "Apresentação do PowerPoint",
            "dom_casmurro_final",
            "---",
        ],
    )
    fun `ignores titles that are file names or placeholders`(raw: String) {
        assertThat(DocumentInfoCleaner.title(raw)).isNull()
    }

    @Test
    fun `ignores a missing or absurdly long title`() {
        assertThat(DocumentInfoCleaner.title(null)).isNull()
        assertThat(DocumentInfoCleaner.title("palavra ".repeat(40))).isNull()
    }

    @Test
    fun `splits authors on semicolons and keeps names written either way`() {
        assertThat(DocumentInfoCleaner.authors("Machado de Assis; José de Alencar"))
            .containsExactly("Machado de Assis", "José de Alencar")
            .inOrder()
        assertThat(DocumentInfoCleaner.authors("Assis, Machado de")).containsExactly("Assis, Machado de")
        assertThat(DocumentInfoCleaner.authors("Clarice")).containsExactly("Clarice")
    }

    @Test
    fun `leaves out computer users, placeholders and contact details`() {
        listOf(null, "", "Usuário", "Windows User", "admin", "Administrador", "Proprietário", "HP", "PC01", "jsilva")
            .forEach { assertThat(DocumentInfoCleaner.authors(it)).isEmpty() }
        assertThat(
            DocumentInfoCleaner.authors("leitor@example.com; Machado de Assis"),
        ).containsExactly("Machado de Assis")
    }

    @Test
    fun `SR-13 keeps at most twenty authors, each cut to the name limit`() {
        val names = List(10_000) { "Autor Número ${"Longo ".repeat(it % 3 * 100)}" }

        val authors = DocumentInfoCleaner.authors(names.joinToString(";"))

        assertThat(authors).hasSize(MetadataLimits.MAX_AUTHORS)
        authors.forEach { assertThat(it.length).isAtMost(MetadataLimits.MAX_AUTHOR_LENGTH) }
        assertThat(authors.first()).isEqualTo("Autor Número")
    }

    @Test
    fun `SR-13 refuses a language tag longer than the limit`() {
        assertThat(DocumentInfoCleaner.language("pt-${"BR-".repeat(100_000)}x")).isNull()
    }

    @Test
    fun `keeps language tags only`() {
        assertThat(DocumentInfoCleaner.language("pt-BR")).isEqualTo("pt-BR")
        assertThat(DocumentInfoCleaner.language(" pt_BR ")).isEqualTo("pt-BR")
        assertThat(DocumentInfoCleaner.language("en")).isEqualTo("en")
        listOf(
            null,
            "",
            "x-none",
            "Portuguese",
            "pt BR",
        ).forEach { assertThat(DocumentInfoCleaner.language(it)).isNull() }
    }
}
