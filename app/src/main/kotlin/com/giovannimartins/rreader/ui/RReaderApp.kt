package com.giovannimartins.rreader.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.ui.importer.ImportScreen
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.importer.messageRes
import com.giovannimartins.rreader.ui.library.LibraryRoute
import com.giovannimartins.rreader.ui.player.MiniPlayer
import com.giovannimartins.rreader.ui.player.NowPlaying
import com.giovannimartins.rreader.ui.player.PlayerRoute
import com.giovannimartins.rreader.ui.player.PlayerViewModel
import com.giovannimartins.rreader.ui.voices.VoicesRoute

/**
 * App shell: bottom navigation with the mini player above it, the full player over everything when it is
 * open, and snackbar feedback for imports. [openPlayerRequests] grows each time the notification asks to open
 * the player.
 */
@Composable
fun RReaderApp(
    importViewModel: ImportViewModel,
    openPlayerRequests: Int,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(TopDestination.LIBRARY) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = importState.message?.let { stringResource(it.messageRes()) }

    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) playerOpen = true
    }
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        // After a file arrives, show the library so the new book is visible.
        playerOpen = false
        destination = TopDestination.LIBRARY
        snackbarHostState.showSnackbar(messageText)
        importViewModel.messageShown()
    }

    val current = nowPlaying
    Box(modifier = modifier.fillMaxSize()) {
        if (playerOpen && current != null) {
            BackHandler { playerOpen = false }
            PlayerRoute(
                onMinimize = { playerOpen = false },
                onOpenVoices = {
                    playerOpen = false
                    destination = TopDestination.VOICES
                },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    ShellBottomBar(
                        nowPlaying = current,
                        destination = destination,
                        onDestinationSelect = { destination = it },
                        onOpenPlayer = { playerOpen = true },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSeekBack = playerViewModel::seekBack,
                    )
                },
            ) { padding ->
                ShellDestination(
                    destination = destination,
                    receivingBook = importState.receiving,
                    onFileSelect = importViewModel::import,
                    onNavigate = { destination = it },
                    onOpenPlayer = { playerOpen = true },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

/** The mini player, while a book is open, above the navigation bar. */
@Composable
private fun ShellBottomBar(
    nowPlaying: NowPlaying?,
    destination: TopDestination,
    onDestinationSelect: (TopDestination) -> Unit,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (nowPlaying != null) {
            MiniPlayer(
                nowPlaying = nowPlaying,
                onOpen = onOpenPlayer,
                onTogglePlayPause = onTogglePlayPause,
                onSeekBack = onSeekBack,
            )
        }
        NavigationBar {
            TopDestination.entries.forEach { item ->
                NavigationBarItem(
                    selected = destination == item,
                    onClick = { onDestinationSelect(item) },
                    icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                    label = { Text(stringResource(item.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ShellDestination(
    destination: TopDestination,
    receivingBook: Boolean,
    onFileSelect: (Uri) -> Unit,
    onNavigate: (TopDestination) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (destination) {
            TopDestination.LIBRARY -> {
                LibraryRoute(onAddBook = { onNavigate(TopDestination.ADD_BOOK) }, onOpenPlayer = onOpenPlayer)
            }

            TopDestination.ADD_BOOK -> {
                ImportScreen(receiving = receivingBook, onFileSelect = onFileSelect)
            }

            TopDestination.VOICES -> {
                VoicesRoute()
            }
        }
    }
}
