package com.shareef.videoplayersj.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shareef.videoplayersj.data.saf.SafFolderScanner
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.library.components.EmptyLibraryState
import com.shareef.videoplayersj.ui.library.components.MovieListItem
import com.shareef.videoplayersj.ui.library.components.ShowListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    appContainer: AppContainer,
    onShowClick: (Long) -> Unit,
    onVideoClick: (Long) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(appContainer.libraryRepository, appContainer.folderRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.onFolderPicked(uri, SafFolderScanner.displayNameForTree(context, uri))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VideoPlayerSJ") },
                actions = {
                    IconButton(onClick = { viewModel.rescan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add folder")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.shows.isEmpty() && uiState.movies.isEmpty() && !uiState.isScanning) {
                EmptyLibraryState(onAddFolder = { folderPicker.launch(null) })
            } else {
                LazyColumn {
                    if (uiState.shows.isNotEmpty()) {
                        item { Text("Shows", modifier = Modifier.padding(16.dp)) }
                        items(uiState.shows, key = { it.id }) { show ->
                            ShowListItem(show = show, onClick = { onShowClick(show.id) })
                        }
                    }
                    if (uiState.movies.isNotEmpty()) {
                        item { Text("Movies & Other", modifier = Modifier.padding(16.dp)) }
                        items(uiState.movies, key = { it.id }) { video ->
                            MovieListItem(video = video, onClick = { onVideoClick(video.id) })
                        }
                    }
                }
            }
            if (uiState.isScanning) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
