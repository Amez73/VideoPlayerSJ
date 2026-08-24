package com.shareef.videoplayersj.ui.showdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.model.LibraryVideo

@Composable
fun EpisodeListItem(episode: LibraryVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val label = buildString {
            if (episode.season != null && episode.episode != null) {
                append("S%02dE%02d".format(episode.season, episode.episode))
            } else if (episode.episode != null) {
                append("Episode ${episode.episode}")
            }
            if (!episode.episodeTitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append(episode.episodeTitle)
            }
        }
        Text(label.ifBlank { episode.displayTitle }, style = MaterialTheme.typography.titleMedium)
        val duration = episode.durationMs
        if (duration != null && duration > 0) {
            val progress = (episode.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            if (progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
