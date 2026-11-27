package com.giovannimartins.rreader.ui.explore

import androidx.annotation.StringRes
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.catalog.DownloadFailure
import com.giovannimartins.rreader.catalog.SearchFailure

/** What the listener is told when a search fails. */
@StringRes
fun SearchFailure.messageRes(): Int =
    when (this) {
        SearchFailure.OFFLINE -> R.string.explore_search_offline
        SearchFailure.UNAVAILABLE -> R.string.explore_search_unavailable
        SearchFailure.SITE_DOWN -> R.string.explore_site_down
    }

/** What the listener is told when a book could not be downloaded. */
@StringRes
fun DownloadFailure.messageRes(): Int =
    when (this) {
        DownloadFailure.NETWORK -> R.string.explore_failure_network
        DownloadFailure.UNAVAILABLE -> R.string.explore_failure_unavailable
        DownloadFailure.SITE_DOWN -> R.string.explore_site_down
        DownloadFailure.NOT_FOUND -> R.string.explore_failure_not_found
        DownloadFailure.TOO_LARGE -> R.string.explore_failure_too_large
        DownloadFailure.NOT_A_BOOK -> R.string.explore_failure_not_a_book
        DownloadFailure.STORAGE -> R.string.import_failure_storage
    }
