package com.giovannimartins.rreader.ui.importer

import androidx.annotation.StringRes
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.db.ImportFailure

/** User-facing explanation for each import failure. */
@StringRes
fun ImportFailure.messageRes(): Int =
    when (this) {
        ImportFailure.UNSUPPORTED_FORMAT -> R.string.import_failure_unsupported
        ImportFailure.FORMAT_NOT_YET_SUPPORTED -> R.string.import_failure_not_yet_supported
        ImportFailure.DRM_PROTECTED -> R.string.import_failure_drm
        ImportFailure.CORRUPTED -> R.string.import_failure_corrupted
        ImportFailure.TOO_LARGE -> R.string.import_failure_too_large
        ImportFailure.NO_READABLE_TEXT -> R.string.import_failure_no_text
        ImportFailure.STORAGE_ERROR -> R.string.import_failure_storage
    }

/** Text for a one-off import message. */
@StringRes
fun ImportMessage.messageRes(): Int =
    when (this) {
        ImportMessage.Queued -> R.string.import_queued
        ImportMessage.Duplicate -> R.string.import_duplicate
        is ImportMessage.Rejected -> reason.messageRes()
    }
