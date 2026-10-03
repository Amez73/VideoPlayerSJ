package com.shareef.videoplayersj.desktop.ui.library

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.TooltipArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.ui.common.MediaCard
import com.shareef.videoplayersj.desktop.ui.common.chooseFolder
import com.shareef.videoplayersj.desktop.ui.common.openInBrowser
import com.shareef.videoplayersj.desktop.ui.common.revealInFileManager
import com.shareef.videoplayersj.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    isVlcAvailable: Boolean,
    onShowClick: (Long) -> Unit,
    onVideoClick: (LibraryVideo) -> Unit,
) {
    val view by repository.view.collectAsState()
    val isScanning by repository.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var showFolders by remember { mutableStateOf(false) }

    val addFolder: () -> Unit = {
        chooseFolder()?.let { dir -> scope.launch { repository.addFolder(dir) } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LibraryTopBar(
            query = query,
            onQueryChange = { query = it },
            isScanning = isScanning,
            onRefresh = { scope.launch { repository.rescanAll() } },
            onManageFolders = { showFolders = true },
            onAddFolder = addFolder,
        )

        if (!isVlcAvailable) VlcMissingBanner()

        Box(Modifier.fillMaxSize()) {
            if (view.isEmpty) {
                if (isScanning) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    EmptyLibraryState(onAddFolder = addFolder)
                }
            } else {
                LibraryGrid(
                    query = query,
                    repository = repository,
                    onShowClick = onShowClick,
                    onVideoClick = onVideoClick,
                )
            }
        }
    }

    if (showFolders) {
        FoldersDialog(repository = repository, onAddFolder = addFolder, onDismiss = { showFolders = false })
    }
}

@Composable
private fun LibraryGrid(
    query: String,
    repository: LibraryRepository,
    onShowClick: (Long) -> Unit,
    onVideoClick: (LibraryVideo) -> Unit,
) {
    val view by repository.view.collectAsState()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val needle = query.trim().lowercase()
    fun matches(text: String?) = needle.isEmpty() || text?.lowercase()?.contains(needle) == true
    val continueWatching = if (needle.isEmpty()) view.continueWatching else emptyList()
    val shows = view.shows.filter { matches(it.canonicalTitle) }
    val movies = view.movies.filter { matches(it.displayTitle) }

    fun videoMenu(video: LibraryVideo): List<ContextMenuItem> = listOf(
        ContextMenuItem(if (video.isFinished) "Mark as unwatched" else "Mark as watched") {
            scope.launch { repository.setWatched(video.id, !video.isFinished) }
        },
        ContextMenuItem("Show in folder") { revealInFileManager(video.path) },
    )

    Box(Modifier.fillMaxSize()) {
        // Keys are prefixed per section: shows and videos have independent ids, so a bare id
        // collides across sections and the grid rejects duplicate keys.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (continueWatching.isNotEmpty()) {
                sectionHeader("Continue watching")
                items(continueWatching, key = { "continue-${it.video.id}" }) { item ->
                    val video = item.video
                    MediaCard(
                        title = item.showTitle ?: video.displayTitle,
                        subtitle = if (item.showTitle != null) video.episodeLabel else remainingLabel(video),
                        thumbnailVideoId = video.id,
                        thumbnailPath = video.path,
                        progress = video.progressFraction,
                        onClick = { onVideoClick(video) },
                        contextMenu = { videoMenu(video) },
                    )
                }
            }
            if (shows.isNotEmpty()) {
                sectionHeader("Shows", count = shows.size)
                items(shows, key = { "show-${it.id}" }) { show ->
                    val thumb = show.thumbnailVideo
                    MediaCard(
                        title = show.canonicalTitle,
                        subtitle = buildString {
                            append("${show.episodeCount} episode${if (show.episodeCount == 1) "" else "s"}")
                            if (show.watchedCount > 0) append(" · ${show.watchedCount} watched")
                        },
                        thumbnailVideoId = thumb?.id,
                        thumbnailPath = thumb?.path,
                        badge = show.episodeCount.toString(),
                        isWatched = show.watchedCount == show.episodeCount,
                        onClick = { onShowClick(show.id) },
                    )
                }
            }
            if (movies.isNotEmpty()) {
                sectionHeader("Movies & other", count = movies.size)
                items(movies, key = { "movie-${it.id}" }) { video ->
                    MediaCard(
                        title = video.displayTitle,
                        subtitle = video.durationMs?.let(::formatDuration),
                        thumbnailVideoId = video.id,
                        thumbnailPath = video.path,
                        progress = video.progressFraction.takeIf { !video.isFinished },
                        isWatched = video.isFinished,
                        onClick = { onVideoClick(video) },
                        contextMenu = { videoMenu(video) },
                    )
                }
            }
            if (shows.isEmpty() && movies.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Nothing matches \"$query\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(gridState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp).padding(vertical = 4.dp),
        )
    }
}

private fun remainingLabel(video: LibraryVideo): String? {
    val duration = video.durationMs ?: return null
    val remaining = (duration - video.positionMs).coerceAtLeast(0)
    return "${formatDuration(remaining)} left"
}

private fun LazyGridScope.sectionHeader(title: String, count: Int? = null) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "header-$title", contentType = "header") {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (count != null) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onManageFolders: () -> Unit,
    onAddFolder: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp),
    ) {
        Icon(
            Icons.Rounded.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            "VideoPlayerSJ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        SearchField(query, onQueryChange, Modifier.widthIn(max = 360.dp).weight(2f, fill = false))
        Spacer(Modifier.width(16.dp))
        TooltipIconButton("Rescan folders", onClick = onRefresh, enabled = !isScanning) {
            if (isScanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan folders")
            }
        }
        TooltipIconButton("Manage folders", onClick = onManageFolders) {
            Icon(Icons.Outlined.Folder, contentDescription = "Manage folders")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onAddFolder) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add folder", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search your library", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    tooltip,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        delayMillis = 500,
    ) {
        IconButton(onClick = onClick, enabled = enabled) { content() }
    }
}

@Composable
private fun EmptyLibraryState(onAddFolder: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(
            Icons.Outlined.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text("No videos yet", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Point VideoPlayerSJ at a folder of downloaded shows and movies.\n" +
                "Episodes are grouped into shows automatically.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onAddFolder) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add folder", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun VlcMissingBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                "VLC wasn't found, so videos can't play or show thumbnails. Install VLC (64-bit) and restart the app.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openInBrowser("https://www.videolan.org/vlc/") }) { Text("Get VLC") }
        }
    }
}
