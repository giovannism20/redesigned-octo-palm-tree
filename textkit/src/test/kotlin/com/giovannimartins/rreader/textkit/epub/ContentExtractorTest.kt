package com.giovannimartins.rreader.textkit.epub

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentExtractorTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `cuts paragraphs at block elements, including text directly inside a div`() {
        val body =
            """
            <h1>Capítulo 1</h1>
            <p>Primeiro parágrafo.</p>
            <div>Texto solto <p>Dentro.</p> fim</div>
            <ul><li>Item um</li><li>Item <em>dois</em></li></ul>
            <blockquote><p>Citação.</p></blockquote>
            <table><tr><th>Nome</th><td>Valor</td></tr></table>
            <dl><dt>Termo</dt><dd>Definição</dd></dl>
            <figure><img src="foto.jpg" alt="não lido"/><figcaption>Legenda</figcaption></figure>
            <pre>  linha um
              linha dois </pre>
            """.trimIndent()

        assertThat(extract(body).paragraphs)
            .containsExactly(
                "Capítulo 1",
                "Primeiro parágrafo.",
                "Texto solto",
                "Dentro.",
                "fim",
                "Item um",
                "Item dois",
                "Citação.",
                "Nome",
                "Valor",
                "Termo",
                "Definição",
                "Legenda",
                "linha um linha dois",
            ).inOrder()
    }

    @Test
    fun `joins inline text and turns line breaks into spaces`() {
        val body = "<p>Uma <em>só</em> fra<span>se</span>.<br/>Outra linha.</p><p>Antes<br>depois</p>"

        assertThat(extract(body).paragraphs).containsExactly("Uma só frase. Outra linha.", "Antes depois").inOrder()
    }

    @Test
    fun `normalizes entities, no-break spaces, soft hyphens and zero-width characters`() {
        val body =
            "<p>  Muitos \n\t espaços&nbsp;e&#160;mais, " +
                "hi&shy;fe&#173;ni\u00ADza\u200Bção &amp; &mdash; fim </p>"

        assertThat(extract(body).paragraphs).containsExactly("Muitos espaços e mais, hifenização & — fim")
    }

    @Test
    fun `keeps Portuguese accents, punctuation and dialogue dashes`() {
        val body =
            "<p>— Não sei, coração — disse ela. «Ação!» Será? Pôr-do-sol… " +
                "E a<span>\u0301</span>gua.</p>"

        assertThat(extract(body).paragraphs)
            .containsExactly("— Não sei, coração — disse ela. «Ação!» Será? Pôr-do-sol… E água.")
    }

    @Test
    fun `skips footnote references and footnote bodies but keeps other asides`() {
        val body =
            """
            <p>Texto<a epub:type="noteref" href="#n1">1</a> com nota<sup>2</sup>
            e outra<sup><a href="#n3">c</a></sup>.</p>
            <aside epub:type="footnote" id="n1"><p>Corpo da nota.</p></aside>
            <aside epub:type="endnote"><p>Nota final.</p></aside>
            <div epub:type="rearnote"><p>Nota atrás.</p></div>
            <aside role="doc-footnote"><p>Nota ARIA.</p></aside>
            <p>O 1<sup>o</sup> lugar, M<sup>me</sup> Silva<span role="doc-noteref">*</span> e x<sup>[4]</sup>.</p>
            <aside><p>Quadro lateral.</p></aside>
            """.trimIndent()

        assertThat(extract(body).paragraphs)
            .containsExactly("Texto com nota e outra.", "O 1o lugar, Mme Silva e x.", "Quadro lateral.")
            .inOrder()
    }

    @Test
    fun `skips the head, scripts, styles, navigation and ruby annotations`() {
        val body =
            """
            <nav epub:type="toc"><ol><li><a href="c1.xhtml">Capítulo 1</a></li></ol></nav>
            <script>var lido = false;</script>
            <style>p { color: red; }</style>
            <p><ruby>漢<rp>(</rp><rt>kan</rt><rp>)</rp></ruby>字 fica.</p>
            """.trimIndent()

        assertThat(extract(body).paragraphs).containsExactly("漢字 fica.")
    }

    @Test
    fun `finds the first heading that has text`() {
        val withHeading = "<h2><img src='x.png'/></h2><p>Antes.</p><h3>Capítulo <em>Um</em><sup>1</sup></h3><h1>B</h1>"

        assertThat(extract(withHeading).heading).isEqualTo("Capítulo Um")
        assertThat(extract("<p>Sem título.</p><h4>Pequeno</h4>").heading).isNull()
    }

    @Test
    fun `points each anchor at the first paragraph that starts at or after it`() {
        val body =
            """
            <h2 id="bloco">Capítulo 1</h2>
            <p><a id="inicio"/>Começo do parágrafo.</p>
            <p>Fim de frase.<a id="depois"></a></p>
            <div id="vazio"></div>
            <p>Seguinte <span id="meio">no meio</span>.</p>
            <a name="antigo"></a><p id="dois" class="x">Com nome.</p>
            <aside epub:type="footnote" id="nota"><p>Nota.</p></aside>
            <p id="dois">Repetido.</p>
            <a id="final"/>
            """.trimIndent()

        val anchors = extract(body).anchors

        assertThat(anchors)
            .containsExactly(
                "bloco",
                0,
                "inicio",
                1,
                "depois",
                3,
                "vazio",
                3,
                "meio",
                4,
                "antigo",
                4,
                "dois",
                4,
                "nota",
                5,
                "final",
                6,
            )
    }

    @Test
    fun `records every heading with the paragraphs it takes`() {
        val document =
            extract("<h1>Livro</h1><p>Texto.</p><h5> <a id='i'>I</a></h5><h4>Do <em>título</em></h4><h2></h2>")

        assertThat(document.headings.map { Triple(it.level, it.firstParagraph, it.text) })
            .containsExactly(Triple(1, 0, "Livro"), Triple(5, 2, "I"), Triple(4, 3, "Do título"))
            .inOrder()
        assertThat(document.headings.map { it.endParagraph }).containsExactly(1, 3, 4).inOrder()
        assertThat(extract("<h4>Pequeno</h4><h3>Título</h3>").heading).isEqualTo("Título")
    }

    @Test
    fun `records chapter divisions that have text`() {
        val body =
            "<p>Antes.</p><div class=\"chapter\"><h2>I</h2><p>Um.</p></div>" +
                "<section epub:type=\"bodymatter chapter\"><p>Dois.</p></section>" +
                "<div class=\"chapter\"><img src=\"a.png\" alt=\"\"/></div><div class=\"x chapter\">Três.</div>"

        assertThat(extract(body).chapterDivisions).containsExactly(1, 3, 4).inOrder()
    }

    @Test
    fun `reads single-line labels for titles`() {
        val document = SafeXml.parse("<a href='c1.xhtml'>Capítulo<br/>Primeiro<sup>1</sup></a>".toByteArray(), "nav")

        assertThat(ContentExtractor.inlineText(document.elementsNamed("a").single())).isEqualTo("Capítulo Primeiro")
    }

    @Test
    fun `decodes the encoding declared by the document`() {
        val latin1 =
            "<?xml version='1.0' encoding='ISO-8859-1'?><html><body><p>ação</p></body></html>"
                .toByteArray(Charsets.ISO_8859_1)
        val utf16 = "\uFEFF<html><body><p>coração</p></body></html>".toByteArray(Charsets.UTF_16LE)

        assertThat(ContentExtractor.extract(SafeXml.parse(latin1, "a.xhtml")).paragraphs).containsExactly("ação")
        assertThat(ContentExtractor.extract(SafeXml.parse(utf16, "b.xhtml")).paragraphs).containsExactly("coração")
    }

    @Test
    fun `SR-3 the XML parser neither loads external entities nor expands declared ones`() {
        val secret = File(directory, "secret.txt").apply { writeText("SEGREDO-DO-DISCO") }
        val dtd = File(directory, "evil.dtd").apply { writeText("<!ENTITY remote \"SEGREDO-DO-DTD\">") }
        val document =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html SYSTEM "${dtd.toURI()}" [
              <!ENTITY xxe SYSTEM "${secret.toURI()}">
              <!ENTITY lol "lol">
              <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
              <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
            ]>
            <html xmlns="http://www.w3.org/1999/xhtml"><body><p>A&xxe;B&remote;C&lol3;D</p></body></html>
            """.trimIndent()

        val text = ContentExtractor.extract(SafeXml.parse(document.toByteArray(), "c.xhtml")).paragraphs.joinToString()

        assertThat(text).doesNotContain("SEGREDO")
        assertThat(text).doesNotContain("lollol")
        assertThat(text).isEqualTo("A&xxe;B&remote;C&lol3;D")
    }

    private fun extract(body: String): ContentDocument {
        val document =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="pt-BR">
            <head><title>Título da página</title><style>p { margin: 0; }</style></head>
            <body>$body</body>
            </html>
            """.trimIndent()
        return ContentExtractor.extract(SafeXml.parse(document.toByteArray(), "OEBPS/c1.xhtml"))
    }
}
