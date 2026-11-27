package com.giovannimartins.rreader

import android.util.Log

/**
 * Minimal diagnostics for failures that are mapped to typed errors. Only the
 * exception type is logged: messages can contain file paths or book content,
 * which must not reach logs (SR-8).
 */
internal object Diagnostics {
    private const val TAG = "RReader"

    fun recordHandled(
        where: String,
        error: Throwable,
    ) {
        Log.w(TAG, "$where: ${error.javaClass.simpleName}")
    }

    /** Something that was not done because it killed the app before, such as a call into native code (SR-4). */
    fun recordSkipped(what: String) {
        Log.w(TAG, "$what skipped")
    }

    /** A request that was refused with a result [code], such as a session command the service rejected. */
    fun recordRefused(
        what: String,
        code: Int,
    ) {
        Log.w(TAG, "$what refused: $code")
    }
}
