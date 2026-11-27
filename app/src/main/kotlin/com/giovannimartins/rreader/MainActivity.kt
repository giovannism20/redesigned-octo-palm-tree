package com.giovannimartins.rreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.giovannimartins.rreader.ui.RReaderApp
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.player.PlaybackConnection
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity hosting the Compose UI; also receives books shared or opened from other apps, and opens
 * the player when the playback notification is tapped. The UI is connected to the playback service while
 * the activity is visible.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val importViewModel: ImportViewModel by viewModels()

    @Inject
    lateinit var playbackConnection: PlaybackConnection

    private var openPlayerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(playbackConnection)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            RReaderTheme {
                RReaderApp(importViewModel = importViewModel, openPlayerRequests = openPlayerRequests)
            }
        }
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
