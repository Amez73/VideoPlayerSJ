package com.shareef.videoplayersj.ui.player.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.util.formatDuration

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isDragging) dragValue else (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val displayedPositionMs = if (isDragging) (dragValue * duration).toLong() else positionMs

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatDuration(displayedPositionMs), color = Color.White)
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                isDragging = false
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(formatDuration(durationMs), color = Color.White)
    }
}
