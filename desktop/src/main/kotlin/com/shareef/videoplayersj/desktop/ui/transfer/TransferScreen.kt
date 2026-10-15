package com.shareef.videoplayersj.desktop.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.ui.common.chooseFiles
import com.shareef.videoplayersj.desktop.ui.common.chooseFolder
import com.shareef.videoplayersj.desktop.ui.common.focusRing
import com.shareef.videoplayersj.desktop.ui.common.revealInFileManager
import com.shareef.videoplayersj.desktop.ui.library.TooltipIconButton
import com.shareef.videoplayersj.torrent.DownloadFormat
import com.shareef.videoplayersj.transfer.IncomingOffer
import com.shareef.videoplayersj.transfer.LocalTransfer
import com.shareef.videoplayersj.transfer.OutgoingFile
import com.shareef.videoplayersj.transfer.Peer
import com.shareef.videoplayersj.transfer.Transfer
import com.shareef.videoplayersj.transfer.TransferDirection
import com.shareef.videoplayersj.transfer.TransferState
import java.io.File

/** Sends files to the phone app, and lists what's been sent and received, over the same Wi-Fi. */
@Composable
fun TransferScreen(transfer: LocalTransfer, deviceName: String, onBack: () -> Unit) {
    val peers by transfer.peers.collectAsState()
    val transfers by transfer.transfers.collectAsState()

    fun sendTo(peer: Peer) {
        val files = chooseFiles("Choose files to send to ${peer.name}")
        if (files.isEmpty()) return
        transfer.send(peer, files.map { file -> OutgoingFile(file.name, file.length()) { file.inputStream() } })
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Send files", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item {
                Text(
                    "Open Send files in VideoPlayerSJ on your phone, on the same Wi-Fi. This computer shows up there as \"$deviceName\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionHeader("Nearby devices") }
            if (peers.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Looking for devices… If your phone never appears, allow VideoPlayerSJ through Windows Firewall on private networks.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            items(peers, key = { "peer-${it.id}" }) { peer ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Icon(
                            if (peer.platform == "android") Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(peer.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Button(onClick = { sendTo(peer) }, modifier = Modifier.focusRing(ButtonDefaults.shape)) { Text("Send files…") }
                    }
                }
            }

            if (transfers.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Transfers", Modifier.weight(1f))
                        TextButton(onClick = { transfer.clearFinished() }) { Text("Clear finished") }
                    }
                }
                items(transfers, key = { "transfer-${it.id}" }) { item ->
                    TransferRow(item, onCancel = { transfer.cancel(item.id) })
                }
            }
        }
    }
}

/** Asks whether to accept files another device is sending, and where to put them. */
@Composable
fun IncomingFilesDialog(offer: IncomingOffer, initialDir: File, onAccept: (File) -> Unit, onDecline: () -> Unit) {
    var dir by remember(offer.id) { mutableStateOf(initialDir) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${offer.fromName} wants to send you files") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${offer.files.size} ${if (offer.files.size == 1) "file" else "files"}, ${DownloadFormat.bytes(offer.totalBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Column {
                    offer.files.take(6).forEach {
                        Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (offer.files.size > 6) {
                        Text("and ${offer.files.size - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Save to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(dir.path, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    FilledTonalButton(onClick = {
                        chooseFolder(title = "Choose where to save the files", startIn = dir)?.let { dir = it }
                    }) { Text("Change…") }
                }
            }
        },
        confirmButton = { Button(onClick = { onAccept(dir) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Decline") } },
    )
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun TransferRow(item: Transfer, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                val arrow = if (item.direction == TransferDirection.Sending) "To" else "From"
                val count = "${item.fileCount} ${if (item.fileCount == 1) "file" else "files"}"
                Text("$arrow ${item.peerName} · $count", style = MaterialTheme.typography.titleSmall)
                if (item.state == TransferState.Running) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    transferSummary(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == TransferState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.isActive) {
                TooltipIconButton("Cancel", onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            } else if (item.state == TransferState.Done && item.savedTo != null) {
                TooltipIconButton("Show in folder", onClick = { revealInFileManager(item.savedTo) }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "Show in folder")
                }
            }
        }
    }
}

private fun transferSummary(item: Transfer): String = when (item.state) {
    TransferState.WaitingForAccept -> "Waiting for ${item.peerName} to accept…"
    TransferState.Running -> buildString {
        append("${DownloadFormat.bytes(item.bytesDone)} of ${DownloadFormat.bytes(item.totalBytes)}")
        if (item.rate > 0) append(" · ").append(DownloadFormat.bytes(item.rate)).append("/s")
        item.currentFile?.let { append(" · ").append(it) }
    }
    TransferState.Done -> "Done · ${DownloadFormat.bytes(item.totalBytes)}" + (item.savedTo?.let { " · saved to $it" } ?: "")
    TransferState.Declined -> "Declined" + (item.error?.let { ": $it" } ?: "")
    TransferState.Failed -> "Failed: ${item.error ?: "unknown error"}"
    TransferState.Cancelled -> "Cancelled"
}
