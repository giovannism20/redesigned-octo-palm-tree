package com.giovannimartins.rreader.ui.voices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.giovannimartins.rreader.Diagnostics

/** The system Settings app: the only app the settings intents below are sent to. */
internal const val SETTINGS_PACKAGE = "com.android.settings"

/** The system screen of text-to-speech engines and voices; not a public constant of the SDK. */
internal const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

/**
 * Opens the system text-to-speech settings, where voices can be downloaded, or the system settings when that screen
 * does not exist. Both intents name the system Settings app as their package, so no other app that declares the same
 * actions can answer them (an implicit intent could open an impostor). When neither opens, nothing happens: the
 * failure is recorded and the listener stays on the Voices screen.
 */
internal fun openTextToSpeechSettings(context: Context) {
    listOf(TTS_SETTINGS_ACTION, Settings.ACTION_SETTINGS).any { action -> startSettings(context, action) }
}

private fun startSettings(
    context: Context,
    action: String,
): Boolean =
    try {
        context.startActivity(Intent(action).setPackage(SETTINGS_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Diagnostics.recordHandled("open system settings", e)
        false
    }
