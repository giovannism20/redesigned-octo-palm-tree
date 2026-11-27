package com.giovannimartins.rreader.catalog

import com.giovannimartins.rreader.BuildConfig

/**
 * The `User-Agent` of catalogue requests: the app's name and version and, when the build has one, a contact address
 * (`CATALOG_CONTACT` in `secrets.properties`), as Project Gutenberg asks of apps that use its catalogue (ADR 0017).
 * Nothing about the phone or the person using it.
 */
object CatalogUserAgent {
    val value: String = of(BuildConfig.VERSION_NAME, BuildConfig.CATALOG_CONTACT)

    /** The agent for app [version] and [contact], of which only printable ASCII is kept, so it fits in a header. */
    internal fun of(
        version: String,
        contact: String,
    ): String {
        val printable = contact.filter { it in ' '..'~' }.trim()
        val details = if (printable.isEmpty()) DESCRIPTION else "$DESCRIPTION; +$printable"
        return "RReader/$version ($details)"
    }

    private const val DESCRIPTION = "Android book reader"
}
