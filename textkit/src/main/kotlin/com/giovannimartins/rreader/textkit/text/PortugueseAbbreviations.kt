package com.giovannimartins.rreader.textkit.text

/** Word lists behind the Portuguese sentence rules. */
internal object PortugueseAbbreviations {
    /**
     * Abbreviations that always come before a name or a number (forms of address, titles, street types and
     * references), so the period after them never ends a sentence. Lowercase, without the period. Words a
     * sentence can end with (`TV`, `DNA`, enclitic pronouns such as `nos`) are deliberately absent.
     */
    val PREFIXES: Set<String> =
        words(
            // Forms of address and titles.
            "sr sra srta srs sras dr dra drs dras exmo exma exmos exmas ilmo ilma ilmos ilmas revmo revma",
            "prof profa profs profas sto sta pe fr mons gen cel maj cap ten sgt cmte eng mr mrs",
            // Places and companies.
            "av pça cia",
            // References and numbering.
            "pág págs pag pags pg pgs pp caps vol vols ed eds ex exs nº nºs núm art arts séc sécs",
            "fl fls fig figs cf obs tel aprox op vs",
        )

    /**
     * Dotted abbreviations that close a phrase (eras and company types). They look like initials but may end
     * a sentence, so they follow the default rule: a sentence ends after them when an uppercase word follows.
     * Abbreviations such as `etc.`, `Ltda.` and `Jr.` behave the same way simply by not being [PREFIXES].
     */
    val DOTTED_SUFFIXES: Set<String> = words("a.C d.C a.c d.c S.A s.a")

    /**
     * Capitalized words that usually open a Portuguese sentence and are not surnames. After a single-letter
     * "initial" they reveal a sentence end instead, as in `... D. Pedro I. Ele governou ...`. Name particles
     * such as `Da` or `Do` are deliberately absent.
     */
    val SENTENCE_STARTERS: Set<String> =
        words(
            // Articles and pronouns.
            "O A Os As Um Uma Uns Umas Eu Ele Ela Eles Elas Nós Você Vocês Isso Isto Aquilo",
            "Este Esta Estes Estas Esse Essa Esses Essas Aquele Aquela Aqueles Aquelas",
            "Seu Sua Seus Suas Meu Minha Nosso Nossa Que Quem Onde Qual Tudo Nada Ninguém Alguém Cada Todos Todas",
            // Conjunctions and adverbs.
            "E Ou Nem Mas Porém Contudo Todavia Então Depois Antes Quando Enquanto Se Como Porque Pois",
            "Não Sim Também Já Ainda Logo Assim Agora Hoje Ontem Amanhã Nunca Sempre Talvez Aqui Muito",
            // Prepositions, contractions and common verbs.
            "Em No Na Nos Nas Ao Aos À Às Por Pelo Pela Pelos Pelas Para Com Sem Sobre Entre Até",
            "Era Foi É Há Houve",
        )

    private fun words(vararg groups: String): Set<String> = groups.flatMap { it.split(' ') }.toSet()
}
