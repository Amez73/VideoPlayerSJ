package com.shareef.videoplayersj.desktop.ui.showdetail

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.input.InputMode
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.ui.common.CardShape
import com.shareef.videoplayersj.desktop.ui.common.OptionsDropdown
import com.shareef.videoplayersj.desktop.ui.common.arrowKeyFocus
import com.shareef.videoplayersj.desktop.ui.common.focusRing
import com.shareef.videoplayersj.desktop.ui.common.isOptionsKey
import com.shareef.videoplayersj.desktop.ui.common.ThumbnailProgressBar
import com.shareef.videoplayersj.desktop.ui.common.VideoThumbnail
import com.shareef.videoplayersj.desktop.ui.common.revealInFileManager
import com.shareef.videoplayersj.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun ShowDetailScreen(
    showId: Long,
    repository: LibraryRepository,
    onBack: () -> Unit,
    onVideoClick: (LibraryVideo) -> Unit,
) {
    val view by repository.view.collectAsState()
    val show = view.shows.firstOrNull { it.id == showId }
    val episodes = view.episodesByShow[showId].orEmpty()
    val scope = rememberCoroutineScope()

    // The episode to offer on the big button: whatever is part-way through, else the first unwatched.
    val upNext = episodes.firstOrNull { !it.isFinished && it.positionMs > 0 }
        ?: episodes.firstOrNull { !it.isFinished }
        ?: episodes.firstOrNull()

    val seasons = episodes.map { it.season }.distinct()
    var selectedSeason by remember(showId) { mutableStateOf(upNext?.season) }
    val visibleEpisodes = if (seasons.size > 1) episodes.filter { it.season == selectedSeason } else episodes

    val listState = rememberLazyListState()

    // Starts on the play button, so a controller user can press A straight away.
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playButton.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .arrowKeyFocus()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape, Key.Backspace -> onBack()
                    else -> return@onKeyEvent false
                }
                true
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    VideoThumbnail(
                        videoId = show?.thumbnailVideo?.id,
                        path = show?.thumbnailVideo?.path,
                        modifier = Modifier.width(360.dp).aspectRatio(16f / 9f).clip(CardShape),
                    )
                    Column(Modifier.padding(start = 28.dp).weight(1f)) {
                        Text(
                            show?.canonicalTitle.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            buildString {
                                if (seasons.size > 1) append("${seasons.size} seasons · ")
                                append("${episodes.size} episode${if (episodes.size == 1) "" else "s"}")
                                val watched = episodes.count { it.isFinished }
                                if (watched > 0) append(" · $watched watched")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (upNext != null) {
                            Button(
                                onClick = { onVideoClick(upNext) },
                                modifier = Modifier
                                    .padding(top = 20.dp)
                                    .focusRequester(playButton)
                                    .focusRing(ButtonDefaults.shape),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                val verb = if (upNext.positionMs > 0 && !upNext.isFinished) "Resume" else "Play"
                                Text("$verb ${shortLabel(upNext)}", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            if (seasons.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        seasons.forEach { season ->
                            FilterChip(
                                selected = season == selectedSeason,
                                onClick = { selectedSeason = season },
                                label = { Text(season?.let { "Season $it" } ?: "Other") },
                                modifier = Modifier.focusRing(FilterChipDefaults.shape),
                            )
                        }
                    }
                }
            }

            items(visibleEpisodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onClick = { onVideoClick(episode) },
                    contextMenu = {
                        listOf(
                            ContextMenuItem(if (episode.isFinished) "Mark as unwatched" else "Mark as watched") {
                                scope.launch { repository.setWatched(episode.id, !episode.isFinished) }
                            },
                            ContextMenuItem("Show in folder") { revealInFileManager(episode.path) },
                        )
                    },
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp).padding(vertical = 4.dp),
        )
    }
}

private fun shortLabel(video: LibraryVideo): String = when {
    video.season != null && video.episode != null -> "S%02dE%02d".format(video.season, video.episode)
    video.episode != null -> "episode ${video.episode}"
    else -> ""
}

@Composable
private fun EpisodeRow(episode: LibraryVideo, onClick: () -> Unit, contextMenu: () -> List<ContextMenuItem>) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val highlighted = hovered || (focused && !InputMode.isPointer)
    var optionsOpen by remember { mutableStateOf(false) }
    val selfFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)

    ContextMenuArea(items = contextMenu) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
                .then(if (focused && !InputMode.isPointer) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .onPreviewKeyEvent { event ->
                    if (!event.isOptionsKey()) return@onPreviewKeyEvent false
                    optionsOpen = true
                    true
                }
                .focusRequester(selfFocus)
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(10.dp),
        ) {
            Box(Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                VideoThumbnail(episode.id, episode.path, Modifier.fillMaxSize())
                if (highlighted) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                val progress = episode.progressFraction
                if (progress != null && !episode.isFinished) {
                    ThumbnailProgressBar(progress, Modifier.align(Alignment.BottomCenter))
                }
                if (optionsOpen) OptionsDropdown(expanded = true, items = contextMenu(), onDismiss = { optionsOpen = false }, returnFocusTo = selfFocus)
            }
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                Text(
                    episode.episodeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val duration = episode.durationMs
                if (duration != null && duration > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (episode.positionMs > 0 && !episode.isFinished) {
                            "${formatDuration(duration - episode.positionMs)} left of ${formatDuration(duration)}"
                        } else {
                            formatDuration(duration)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.isFinished) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
