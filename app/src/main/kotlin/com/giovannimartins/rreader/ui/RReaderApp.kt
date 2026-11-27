package com.giovannimartins.rreader.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giovannimartins.rreader.ui.importer.ImportScreen
import com.giovannimartins.rreader.ui.importer.ImportViewModel
import com.giovannimartins.rreader.ui.importer.messageRes
import com.giovannimartins.rreader.ui.library.LibraryRoute

/** App shell: bottom navigation, snackbar feedback for imports, and the current destination. */
@Composable
fun RReaderApp(
    importViewModel: ImportViewModel,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(TopDestination.LIBRARY) }
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = importState.message?.let { stringResource(it.messageRes()) }

    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        // After a file arrives, show the library so the new book is visible.
        destination = TopDestination.LIBRARY
        snackbarHostState.showSnackbar(messageText)
        importViewModel.messageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (destination) {
            TopDestination.LIBRARY -> {
                LibraryRoute(onAddBook = { destination = TopDestination.ADD_BOOK }, modifier = content)
            }

            TopDestination.ADD_BOOK -> {
                ImportScreen(
                    receiving = importState.receiving,
                    onFileSelect = importViewModel::import,
                    modifier = content,
                )
            }
        }
    }
}
