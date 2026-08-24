package com.shareef.videoplayersj.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.model.LibraryShow
import com.shareef.videoplayersj.ui.common.VideoThumbnail

@Composable
fun ShowListItem(show: LibraryShow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbnailVideoId = show.thumbnailVideoId
        val thumbnailUri = show.thumbnailUri
        if (thumbnailVideoId != null && thumbnailUri != null) {
            VideoThumbnail(
                videoId = thumbnailVideoId,
                uriString = thumbnailUri,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column {
            Text(
                text = show.canonicalTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${show.episodeCount} episode${if (show.episodeCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
