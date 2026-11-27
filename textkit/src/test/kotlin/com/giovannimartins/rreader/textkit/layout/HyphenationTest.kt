package com.giovannimartins.rreader.textkit.layout

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Joining words broken across lines, through [LineJoiner] and in whole books. */
class HyphenationTest {
    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource(
        "pala-, vra dita, palavra dita",
        "hifeni-, zação, hifenização",
        "CAPÍ-, TULO I, CAPÍTULO I",
        "noi-, te escura, noite escura",
        "lei-, te quente, leite quente",
        "capítu-, lo seguinte, capítulo seguinte",
        "me-, nos tempo, menos tempo",
        "qua-, se nada, quase nada",
        "peque-, no grande, pequeno grande",
        "esco-, la nova, escola nova",
        "deta-, lhes finais, detalhes finais",
        "ruí-, na antiga, ruína antiga",
        "cava-, lo branco, cavalo branco",
        "italia-, nos do sul, italianos do sul",
        "par-, te dela, parte dela",
        "fir-, me e forte, firme e forte",
        "ter-, nos azuis, ternos azuis",
        "costu-, me antigo, costume antigo",
        "dis-, se-lhe tudo, disse-lhe tudo",
        "tor-, na-se outro, torna-se outro",
    )
    fun `removes the hyphen of syllable breaks`(
        first: String,
        second: String,
        joined: String,
    ) {
        assertThat(join(first, second)).isEqualTo(joined)
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource(
        "deixou-, o ali, deixou-o ali",
        "chamei-, a depois, chamei-a depois",
        "vi-, os juntos, vi-os juntos",
        "levou-, as embora, levou-as embora",
        "fazê-, lo agora, fazê-lo agora",
        "pô-, la na mesa, pô-la na mesa",
        "amá-, los muito, amá-los muito",
        "vendê-, las logo, vendê-las logo",
        "fazem-, no sempre, fazem-no sempre",
        "dão-, na a ele, dão-na a ele",
        "dê-, nos paz, dê-nos paz",
        "põem-, nas ali, põem-nas ali",
        "dá-, lhe razão, dá-lhe razão",
        "disse-, lhes adeus, disse-lhes adeus",
        "chamou-, me baixinho, chamou-me baixinho",
        "deu-, te um livro, deu-te um livro",
        "levantou-, se cedo, levantou-se cedo",
        "dou-, vos a mão, dou-vos a mão",
        "fez-, se silêncio, fez-se silêncio",
        "põe-, se a rir, põe-se a rir",
        "foi-, se embora, foi-se embora",
        "dizia-, se que sim, dizia-se que sim",
        "olhava-, me de longe, olhava-me de longe",
        "dizer-, lhe que não, dizer-lhe que não",
        "tornar-, se rei, tornar-se rei",
        "pedi-, lhe ajuda, pedi-lhe ajuda",
        "amá-, lo-ei sempre, amá-lo-ei sempre",
        "dir-, te-ia tudo, dir-te-ia tudo",
        "far-, se-á justiça, far-se-á justiça",
        "FAZÊ-, LO AGORA, FAZÊ-LO AGORA",
    )
    fun `keeps the hyphen of enclisis and mesoclisis`(
        first: String,
        second: String,
        joined: String,
    ) {
        assertThat(join(first, second)).isEqualTo(joined)
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource(
        "guarda-, -chuva aberto, guarda-chuva aberto",
        "pré-, Amazônia, pré-Amazônia",
        "1990-, 1995, 1990-1995",
        "a palavra -, outra, a palavra - outra",
        "Ele disse —, não vou, Ele disse — não vou",
        "fim, começo, fim começo",
    )
    fun `keeps other hyphens and joins other lines with a space`(
        first: String,
        second: String,
        joined: String,
    ) {
        assertThat(join(first, second)).isEqualTo(joined)
    }

    @Test
    fun `continues a word after a soft hyphen without leaving it in the text`() {
        val text = StringBuilder("pala")
        LineJoiner.append(text, textLine("pala\u00AD", softHyphen = true), "vra")

        assertThat(text.toString()).isEqualTo("palavra")
    }

    @Test
    fun `joins hyphenated words inside paragraphs and across pages`() {
        val first =
            page(0) {
                full("Ele não queria fazê-", indent = true)
                full("lo naquela manhã, mas a noi-")
                full("te chegou e o hifeni\u00AD")
                full("zado capítulo continuou na pá-")
            }
        val second = page(1) { last("gina seguinte, dá-lhe razão.") }

        assertThat(paragraphsOf(first, second))
            .containsExactly(
                "Ele não queria fazê-lo naquela manhã, mas a noite chegou e o hifenizado capítulo continuou na " +
                    "página seguinte, dá-lhe razão.",
            )
    }

    private fun join(
        first: String,
        second: String,
    ): String {
        val text = StringBuilder(first)
        LineJoiner.append(text, textLine(first), second)
        return text.toString()
    }

    private fun textLine(
        text: String,
        softHyphen: Boolean = false,
    ) = TextLine(text, LayoutLine(text, 0f, 0f, 1f, 1f, 1f), 0, softHyphen)
}
