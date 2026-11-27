package com.giovannimartins.rreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.giovannimartins.rreader.ui.RReaderApp
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity hosting the Compose UI; also receives books shared or opened from other apps. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val importViewModel: ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncomingBook(intent)
        setContent {
            RReaderTheme {
                RReaderApp(importViewModel = importViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingBook(intent)
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
