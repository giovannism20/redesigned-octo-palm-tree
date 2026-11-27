package com.giovannimartins.rreader

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.tts.neural.VoiceDownloads
import com.giovannimartins.rreader.ui.RReaderApp
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.player.PlaybackConnection
import com.giovannimartins.rreader.ui.settings.SettingsViewModel
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.giovannimartins.rreader.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity hosting the Compose UI; also receives books shared or opened from other apps, and opens
 * the player when the playback notification is tapped. The UI is connected to the playback service while
 * the activity is visible. Every screen is drawn with the listener's appearance ([SettingsViewModel]), which
 * applies as soon as it changes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val importViewModel: ImportViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var playbackConnection: PlaybackConnection

    @Inject
    lateinit var voiceDownloads: VoiceDownloads

    private var openPlayerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen stays until the appearance is read, so the first screen already has the listener's
        // theme and text size.
        installSplashScreen().setKeepOnScreenCondition { settingsViewModel.appearance.value == null }
        super.onCreate(savedInstanceState)
        showSystemBarsFor(darkTheme = true)
        lifecycle.addObserver(playbackConnection)
        if (savedInstanceState == null) {
            handleIntent(intent)
            // Here rather than in the Application, which every process of the app and every unit test creates.
            lifecycleScope.launch { voiceDownloads.discardLeftovers() }
        }
        setContent {
            val appearance by settingsViewModel.appearance.collectAsStateWithLifecycle()
            val shown = appearance ?: Appearance()
            val darkTheme = shown.theme.isDark()
            DisposableEffect(darkTheme) {
                showSystemBarsFor(darkTheme)
                onDispose {}
            }
            RReaderTheme(darkTheme = darkTheme, textSize = shown.textSize) {
                RReaderApp(importViewModel = importViewModel, openPlayerRequests = openPlayerRequests)
            }
        }
    }

    /** Draws behind the system bars, with their icons light on the dark theme and dark on the light one. */
    private fun showSystemBarsFor(darkTheme: Boolean) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) openPlayerRequests++ else handleIncomingBook(intent)
    }

    /** Accepts `content://` books from "Share" (ACTION_SEND) and "Open with" (ACTION_VIEW). */
    private fun handleIncomingBook(intent: Intent?) {
        val uri: Uri? =
            when (intent?.action) {
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                Intent.ACTION_VIEW -> intent.data
                else -> null
            }
        if (uri != null) importViewModel.import(uri)
    }

    companion object {
        /** Sent by the playback notification's content intent (explicit, so no intent filter is needed). */
        const val ACTION_OPEN_PLAYER = "com.giovannimartins.rreader.action.OPEN_PLAYER"
    }
}
