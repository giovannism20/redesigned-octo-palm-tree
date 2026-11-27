package com.giovannimartins.rreader.textkit.layout

import com.giovannimartins.rreader.textkit.TextCleaner
import com.giovannimartins.rreader.textkit.model.MetadataLimits

/**
 * Keeps only the meaningful parts of the metadata a document declares about itself (the PDF document information
 * dictionary and catalog language). Word processors and converters often fill it with file names, template names or
 * the login name of whoever made the file, which must not become the book's title or author.
 */
public object DocumentInfoCleaner {
    private const val MAX_TITLE_LENGTH = 200
    private val OFFICE_PREFIX = Regex("(?i)^microsoft (?:word|powerpoint|excel) - ")
    private val FILE_NAME =
        Regex(
            "(?i).*\\.(?:pdf|docx?|dotx?|odt|ott|rtf|txt|pages|indd|qxd|qxp|pub|tex|dvi|ps|html?|xml|epub|mobi|azw3?|" +
                "wpd|wps|cdr|ai|fm|jpe?g|png|tiff?)$",
        )
    private val PLACEHOLDER_TITLE =
        Regex(
            "(?iu)untitled(?: document)?|sem t[íi]tulo|documento sem t[íi]tulo|t[íi]tulo|title|documento?\\s*\\d*|" +
                "apresenta[çc][ãa]o do powerpoint|powerpoint presentation|slide\\s*\\d*|" +
                "unknown|desconhecido|none|null|default|word|pdf|-+",
        )
    private val PLACEHOLDER_AUTHOR =
        Regex(
            "(?iu)(?:user|usu[áa]rio|utilizador|admin|administrador|administrator|owner|propriet[áa]rio|autor|author|" +
                "unknown|desconhecido|windows user|microsoft office user|usu[áa]rio do windows|" +
                "utilizador do windows|usu[áa]rio do microsoft office|pc|hp|dell|lenovo|asus|acer|samsung|positivo)" +
                "\\s*\\d*",
        )

    /**
     * The title, or null when the document has none worth showing: blank, a file name, a placeholder such as
     * "Untitled" or "Documento1", an office export prefix ("Microsoft Word - ..."), a name joined by underscores or
     * longer than [MAX_TITLE_LENGTH] characters.
     */
    public fun title(raw: String?): String? {
        val title = TextCleaner.clean(raw.orEmpty())
        val meaningless =
            title.isEmpty() ||
                title.length > MAX_TITLE_LENGTH ||
                OFFICE_PREFIX.containsMatchIn(title) ||
                FILE_NAME.matches(title) ||
                PLACEHOLDER_TITLE.matches(title) ||
                (' ' !in title && '_' in title)
        return title.takeUnless { meaningless }
    }

    /**
     * The authors, split on semicolons, leaving out placeholders ("Usuário", "Windows User", computer brands),
     * e-mail addresses, names with digits and login names (a single word without capitals). At most
     * [MetadataLimits.MAX_AUTHORS] are kept, each cut to [MetadataLimits.MAX_AUTHOR_LENGTH] characters.
     */
    public fun authors(raw: String?): List<String> =
        raw
            .orEmpty()
            .splitToSequence(';')
            .map(TextCleaner::clean)
            .filter(::isPersonName)
            .take(MetadataLimits.MAX_AUTHORS)
            .map(MetadataLimits::author)
            .toList()

    /**
     * The BCP 47 tag, such as `pt-BR`, or null when [raw] is not one or is longer than
     * [MetadataLimits.MAX_LANGUAGE_LENGTH] characters; underscores become hyphens.
     */
    public fun language(raw: String?): String? =
        MetadataLimits.language(TextCleaner.clean(raw.orEmpty()).replace('_', '-'))

    private fun isPersonName(name: String): Boolean =
        name.any(Char::isLetter) &&
            name.none(Char::isDigit) &&
            '@' !in name &&
            !PLACEHOLDER_AUTHOR.matches(name) &&
            (' ' in name || name.any(Char::isUpperCase))
}
