package com.shareef.videoplayersj.ui.showdetail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.showdetail.components.EpisodeListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    showId: Long,
    appContainer: AppContainer,
    onBack: () -> Unit,
    onVideoClick: (Long) -> Unit,
) {
    val viewModel: ShowDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShowDetailViewModel(appContainer.libraryRepository, showId) }
        },
    )
    val episodes by viewModel.episodes.collectAsState()
    val title by viewModel.title.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(episodes, key = { it.id }) { episode ->
                EpisodeListItem(episode = episode, onClick = { onVideoClick(episode.id) })
            }
        }
    }
}
