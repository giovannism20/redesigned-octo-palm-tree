package com.giovannimartins.rreader.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The background and default content colour every full screen shares. Text and icons without an explicit
 * colour take the theme's content colour from here; without it they fall back to black, which is unreadable
 * on the dark background. Screens shown outside the navigation scaffold (the player and the reader) must use it.
 */
@Composable
fun ScreenSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}
