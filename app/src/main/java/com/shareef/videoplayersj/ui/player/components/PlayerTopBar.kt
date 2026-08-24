package com.shareef.videoplayersj.ui.player.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

private val SLEEP_TIMER_OPTIONS = listOf(15, 30, 45, 60)

@Composable
fun PlayerTopBar(
    title: String,
    isCastAvailable: Boolean,
    sleepTimerMinutes: Int?,
    onBack: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sleepMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)

        IconButton(onClick = { sleepMenuOpen = true }) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = if (sleepTimerMinutes != null) {
                    "Sleep timer: $sleepTimerMinutes minutes"
                } else {
                    "Sleep timer"
                },
                // An armed timer is worth seeing at a glance, since it stops playback later.
                tint = if (sleepTimerMinutes != null) Color(0xFF4FC3F7) else Color.White,
            )
        }
        DropdownMenu(expanded = sleepMenuOpen, onDismissRequest = { sleepMenuOpen = false }) {
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(if (sleepTimerMinutes == minutes) "$minutes minutes ✓" else "$minutes minutes") },
                    onClick = {
                        onSetSleepTimer(minutes)
                        sleepMenuOpen = false
                    },
                )
            }
            if (sleepTimerMinutes != null) {
                DropdownMenuItem(
                    text = { Text("Turn off") },
                    onClick = {
                        onSetSleepTimer(null)
                        sleepMenuOpen = false
                    },
                )
            }
        }

        if (isCastAvailable) {
            AndroidView(
                // Casting is optional, so it must never be able to take the player down with it:
                // MediaRouteButton throws if the host theme/activity don't satisfy its AppCompat
                // requirements, and losing the button beats losing playback.
                factory = { ctx ->
                    runCatching {
                        MediaRouteButton(ctx).apply {
                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                        } as View
                    }.getOrElse { View(ctx) }
                },
            )
        }
    }
}
