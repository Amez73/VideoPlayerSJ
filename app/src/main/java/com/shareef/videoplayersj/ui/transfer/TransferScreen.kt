package com.shareef.videoplayersj.ui.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.torrent.DownloadFormat
import com.shareef.videoplayersj.transfer.IncomingOffer
import com.shareef.videoplayersj.transfer.OutgoingFile
import com.shareef.videoplayersj.transfer.Peer
import com.shareef.videoplayersj.transfer.Transfer
import com.shareef.videoplayersj.transfer.TransferDirection
import com.shareef.videoplayersj.transfer.TransferState
import com.shareef.videoplayersj.ui.common.SaveFolderPicker
import com.shareef.videoplayersj.ui.common.rememberHasFileAccess
import java.io.File

/** Sends files to the desktop app, and lists what's been sent and received, over the same Wi-Fi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(appContainer: AppContainer, onBack: () -> Unit) {
    val transfer = appContainer.localTransfer
    val peers by transfer.peers.collectAsState()
    val transfers by transfer.transfers.collectAsState()
    val context = LocalContext.current
    var sendingTo by remember { mutableStateOf<Peer?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val peer = sendingTo ?: return@rememberLauncherForActivityResult
        sendingTo = null
        val files = uris.mapNotNull { outgoingFile(context, it) }
        if (files.isNotEmpty()) transfer.send(peer, files)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Open Send files in VideoPlayerSJ on your computer, on the same Wi-Fi. " +
                        "This phone shows up there as \"${appContainer.deviceName}\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item { SectionHeader("Nearby devices") }
            if (peers.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Looking for devices…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            items(peers, key = { "peer-${it.id}" }) { peer ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Icon(
                            if (peer.platform == "android") Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            peer.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Button(onClick = {
                            sendingTo = peer
                            filePicker.launch(arrayOf("*/*"))
                        }) { Text("Send files") }
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
                    TransferCard(item, onCancel = { transfer.cancel(item.id) })
                }
            }
        }
    }
}

/** Asks whether to accept files the computer is sending, and where to put them. */
@Composable
fun IncomingFilesDialog(offer: IncomingOffer, initialDir: File, onAccept: (File) -> Unit, onDecline: () -> Unit) {
    var dir by remember(offer.id) { mutableStateOf(initialDir) }
    val hasAccess = rememberHasFileAccess()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${offer.fromName} wants to send you files") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${offer.files.size} ${if (offer.files.size == 1) "file" else "files"}, ${DownloadFormat.bytes(offer.totalBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Column {
                    offer.files.take(4).forEach {
                        Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (offer.files.size > 4) {
                        Text("and ${offer.files.size - 4} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SaveFolderPicker(dir = dir, onDirChange = { dir = it }, hasAccess = hasAccess, purpose = "received files")
            }
        },
        confirmButton = { Button(onClick = { onAccept(dir) }, enabled = hasAccess.value) { Text("Accept") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Decline") } },
    )
}

/** A picked document as something to send, with the name and size the system reports. */
private fun outgoingFile(context: Context, uri: Uri): OutgoingFile? {
    val resolver = context.contentResolver
    var name: String? = null
    var size = -1L
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
    }
    // The receiver needs the exact size up front; providers that can't say are skipped.
    if (size < 0) return null
    return OutgoingFile(name ?: uri.lastPathSegment ?: "file", size) {
        resolver.openInputStream(uri) ?: error("Couldn't open ${name ?: uri}")
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 12.dp))
}

@Composable
private fun TransferCard(item: Transfer, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                val direction = if (item.direction == TransferDirection.Sending) "To" else "From"
                val count = "${item.fileCount} ${if (item.fileCount == 1) "file" else "files"}"
                Text("$direction ${item.peerName} · $count", style = MaterialTheme.typography.titleSmall)
                if (item.state == TransferState.Running) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    transferSummary(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == TransferState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.isActive) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
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
