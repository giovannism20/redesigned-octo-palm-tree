package com.giovannimartins.rreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.giovannimartins.rreader.ui.library.LibraryScreen
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity hosting the Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RReaderTheme {
                LibraryScreen()
            }
        }
    }
}
