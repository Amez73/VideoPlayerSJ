package com.shareef.videoplayersj.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

private val TRACK_LENGTH = 132.dp
private val TOUCH_WIDTH = 40.dp

/**
 * Compact vertical volume slider for the right edge of the player. Compose has no vertical
 * Slider, so a normal one is rotated a quarter turn — `requiredWidth` sets its pre-rotation
 * length independently of the narrow box it sits in, and Compose maps touch events through the
 * rotation so dragging upward raises the volume.
 */
@Composable
fun VolumeControl(onVolumeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var volume by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier.height(TRACK_LENGTH).width(TOUCH_WIDTH),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                onVolumeChange(it)
            },
            modifier = Modifier
                .requiredWidth(TRACK_LENGTH)
                .rotate(270f),
        )
    }
}
