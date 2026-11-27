package com.giovannimartins.rreader.ui

import com.giovannimartins.rreader.R

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    LIBRARY(R.string.tab_library, R.drawable.ic_library),
    EXPLORE(R.string.tab_explore, R.drawable.ic_explore),
    ADD_BOOK(R.string.tab_add_book, R.drawable.ic_add_book),
    VOICES(R.string.tab_voices, R.drawable.ic_voices),
}
