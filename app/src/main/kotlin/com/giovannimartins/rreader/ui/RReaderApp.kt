package com.giovannimartins.rreader.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.ui.about.AboutRoute
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.explore.ExploreRoute
import com.giovannimartins.rreader.ui.importer.ImportScreen
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.importer.messageRes
import com.giovannimartins.rreader.ui.library.LibraryRoute
import com.giovannimartins.rreader.ui.player.MiniPlayer
import com.giovannimartins.rreader.ui.player.NowPlaying
import com.giovannimartins.rreader.ui.player.PlayerRoute
import com.giovannimartins.rreader.ui.player.PlayerViewModel
import com.giovannimartins.rreader.ui.reader.ReaderRoute
import com.giovannimartins.rreader.ui.settings.SettingsRoute
import com.giovannimartins.rreader.ui.voices.VoicesRoute

/**
 * App shell: bottom navigation with the mini player above it, the full player over everything when it is
 * open, the follow-along reader over the player, and snackbar feedback for imports. "Add a book" opens a page of
 * the library tab. [openPlayerRequests] grows each time the notification asks to open the player.
 */
@Composable
fun RReaderApp(
    importViewModel: ImportViewModel,
    openPlayerRequests: Int,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(TopDestination.LIBRARY) }
    var addingBook by rememberSaveable { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var readerOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = importState.message?.let { stringResource(it.messageRes()) }

    /** Shows [tab], closing the add-book page of the library. */
    fun showTab(tab: TopDestination) {
        addingBook = false
        destination = tab
    }

    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) playerOpen = true
    }
    // The reader opens over the player only: it closes with the player or when no book is open anymore.
    val bookOpen = nowPlaying != null
    LaunchedEffect(playerOpen, bookOpen) {
        if (!playerOpen || !bookOpen) readerOpen = false
    }
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        // After a file arrives, show the library so the new book is visible.
        playerOpen = false
        showTab(TopDestination.LIBRARY)
        snackbarHostState.showSnackbar(messageText)
        importViewModel.messageShown()
    }

    val current = nowPlaying
    ScreenSurface(modifier = modifier) {
        if (aboutOpen) {
            AboutRoute(onClose = { aboutOpen = false })
        } else if (playerOpen && current != null) {
            PlayerAndReader(
                readerOpen = readerOpen,
                onReaderOpenChange = { readerOpen = it },
                onMinimize = { playerOpen = false },
                onOpenVoices = {
                    playerOpen = false
                    showTab(TopDestination.VOICES)
                },
            )
        } else {
            ShellTabs(
                nowPlaying = current,
                destination = destination,
                snackbarHostState = snackbarHostState,
                receivingBook = importState.receiving,
                addingBook = addingBook,
                onAddingBookChange = { addingBook = it },
                onNavigate = ::showTab,
                onOpenPlayer = { playerOpen = true },
                onOpenAbout = { aboutOpen = true },
                onFileSelect = importViewModel::import,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSeekBack = playerViewModel::seekBack,
            )
        }
    }
}

/** The tabs with the navigation bar (and the mini player above it while a book is open). */
@Composable
private fun ShellTabs(
    nowPlaying: NowPlaying?,
    destination: TopDestination,
    snackbarHostState: SnackbarHostState,
    receivingBook: Boolean,
    addingBook: Boolean,
    onAddingBookChange: (Boolean) -> Unit,
    onNavigate: (TopDestination) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAbout: () -> Unit,
    onFileSelect: (Uri) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ShellBottomBar(
                nowPlaying = nowPlaying,
                destination = destination,
                onDestinationSelect = onNavigate,
                onOpenPlayer = onOpenPlayer,
                onTogglePlayPause = onTogglePlayPause,
                onSeekBack = onSeekBack,
            )
        },
    ) { padding ->
        ShellDestination(
            destination = destination,
            receivingBook = receivingBook,
            addingBook = addingBook,
            onAddingBookChange = onAddingBookChange,
            onFileSelect = onFileSelect,
            onNavigate = onNavigate,
            onOpenPlayer = onOpenPlayer,
            onOpenAbout = onOpenAbout,
            modifier = Modifier.padding(padding),
        )
    }
}

/** The full player, or the follow-along reader over it; back goes from the reader to the player. */
@Composable
private fun PlayerAndReader(
    readerOpen: Boolean,
    onReaderOpenChange: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onOpenVoices: () -> Unit,
) {
    if (readerOpen) {
        BackHandler { onReaderOpenChange(false) }
        ReaderRoute(onClose = { onReaderOpenChange(false) })
    } else {
        BackHandler(onBack = onMinimize)
        PlayerRoute(onMinimize = onMinimize, onOpenVoices = onOpenVoices, onOpenReader = { onReaderOpenChange(true) })
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
        ShellNavigationBar(destination = destination, onDestinationSelect = onDestinationSelect)
    }
}

@Composable
private fun ShellDestination(
    destination: TopDestination,
    receivingBook: Boolean,
    addingBook: Boolean,
    onAddingBookChange: (Boolean) -> Unit,
    onFileSelect: (Uri) -> Unit,
    onNavigate: (TopDestination) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (destination) {
            TopDestination.LIBRARY -> {
                if (addingBook) {
                    BackHandler { onAddingBookChange(false) }
                    ImportScreen(
                        receiving = receivingBook,
                        onFileSelect = onFileSelect,
                        onClose = { onAddingBookChange(false) },
                    )
                } else {
                    LibraryRoute(onAddBook = { onAddingBookChange(true) }, onOpenPlayer = onOpenPlayer)
                }
            }

            TopDestination.EXPLORE -> {
                ExploreRoute(onOpenLibrary = { onNavigate(TopDestination.LIBRARY) })
            }

            TopDestination.VOICES -> {
                VoicesRoute(onOpenAbout = onOpenAbout)
            }

            TopDestination.SETTINGS -> {
                SettingsRoute()
            }
        }
    }
}
