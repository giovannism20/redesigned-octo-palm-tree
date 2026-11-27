package com.giovannimartins.rreader.catalog

/**
 * A public-domain book offered in the Explore tab: Project Gutenberg's ebook number, its title and author and, for
 * the curated books, a short description in Portuguese.
 */
data class CatalogEntry(
    val id: Int,
    val title: String,
    val author: String?,
    val description: String? = null,
)
