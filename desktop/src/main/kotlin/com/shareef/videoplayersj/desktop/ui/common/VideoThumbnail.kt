package com.shareef.videoplayersj.desktop.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.thumbnails.ThumbnailLoader

val LocalThumbnailLoader = staticCompositionLocalOf<ThumbnailLoader> { error("No ThumbnailLoader provided") }

/**
 * A frame from the video filling the given bounds, with a placeholder icon until it loads (or
 * permanently, for files VLC can't decode). Loading is keyed by [videoId], so reused grid cells
 * request the right frame.
 */
@Composable
fun VideoThumbnail(videoId: Long?, path: String?, modifier: Modifier = Modifier) {
    val loader = LocalThumbnailLoader.current
    val bitmap by produceState(
        initialValue = videoId?.let(loader::cached),
        videoId,
        path,
    ) {
        if (videoId != null && path != null && value == null) {
            value = loader.load(videoId, path)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmap) { frame: ImageBitmap? ->
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
