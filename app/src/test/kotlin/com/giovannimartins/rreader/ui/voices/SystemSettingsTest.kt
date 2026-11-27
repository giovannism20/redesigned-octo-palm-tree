package com.giovannimartins.rreader.ui.voices

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The Voices screen sends the listener to the system Settings app only, never to an app that claims its actions. */
@RunWith(RobolectricTestRunner::class)
class SystemSettingsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Starting an activity nobody declared fails, as on a phone.
        shadowOf(application).checkActivities(true)
    }

    @Test
    fun `opens the text-to-speech settings of the system Settings app`() {
        declare(TTS_SETTINGS_ACTION)

        openTextToSpeechSettings(application)

        val started = shadowOf(application).nextStartedActivity
        assertThat(started.action).isEqualTo(TTS_SETTINGS_ACTION)
        assertThat(started.`package`).isEqualTo(SETTINGS_PACKAGE)
    }

    @Test
    fun `falls back to the system settings when there is no text-to-speech screen`() {
        declare(Settings.ACTION_SETTINGS)

        openTextToSpeechSettings(application)

        val started = shadowOf(application).nextStartedActivity
        assertThat(started.action).isEqualTo(Settings.ACTION_SETTINGS)
        assertThat(started.`package`).isEqualTo(SETTINGS_PACKAGE)
    }

    @Test
    fun `opens nothing, and does not crash, when the system Settings app answers neither`() {
        openTextToSpeechSettings(application)

        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    /** Declares an activity of the system Settings app that answers [action]. */
    private fun declare(action: String) {
        val component = ComponentName(SETTINGS_PACKAGE, "$SETTINGS_PACKAGE.SubSettings")
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(action).apply { addCategory("android.intent.category.DEFAULT") },
        )
    }
}
