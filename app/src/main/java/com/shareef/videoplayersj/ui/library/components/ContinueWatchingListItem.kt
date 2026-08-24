package com.shareef.videoplayersj.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.model.ContinueWatchingItem
import com.shareef.videoplayersj.ui.common.VideoThumbnail

@Composable
fun ContinueWatchingListItem(item: ContinueWatchingItem, onClick: () -> Unit) {
    val video = item.video
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(
            videoId = video.id,
            uriString = video.documentUriString,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column {
            // For an episode the show is the useful headline, with the episode label beneath it;
            // a standalone movie only has its own title.
            Text(
                text = item.showTitle ?: video.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.showTitle != null) {
                Text(
                    text = video.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val duration = video.durationMs
            if (duration != null && duration > 0) {
                val progress = (video.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}
