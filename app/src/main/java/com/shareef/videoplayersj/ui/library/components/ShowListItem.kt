package com.shareef.videoplayersj.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.model.LibraryShow

@Composable
fun ShowListItem(show: LibraryShow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(show.canonicalTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            "${show.episodeCount} episode${if (show.episodeCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
