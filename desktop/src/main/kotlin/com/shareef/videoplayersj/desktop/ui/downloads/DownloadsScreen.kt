package com.shareef.videoplayersj.desktop.ui.downloads

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.torrent.MagnetHandler
import com.shareef.videoplayersj.desktop.ui.common.chooseFolder
import com.shareef.videoplayersj.desktop.ui.common.focusRing
import com.shareef.videoplayersj.desktop.ui.common.revealInFileManager
import com.shareef.videoplayersj.desktop.ui.library.TooltipIconButton
import com.shareef.videoplayersj.torrent.DownloadFormat
import com.shareef.videoplayersj.torrent.DownloadItem
import com.shareef.videoplayersj.torrent.DownloadState
import com.shareef.videoplayersj.torrent.FinishedDownload
import com.shareef.videoplayersj.torrent.Magnet
import com.shareef.videoplayersj.torrent.TorrentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The torrent side of the app: what's downloading, what finished, and a box to paste a magnet
 * link into. Downloads stop and are removed as soon as they complete; nothing is seeded.
 */
@Composable
fun DownloadsScreen(
    engine: TorrentEngine,
    libraryRepository: LibraryRepository,
    onAddMagnet: (String) -> Unit,
    onBack: () -> Unit,
) {
    val downloads by engine.downloads.collectAsState()
    val finished by engine.finished.collectAsState()
    val startError by engine.startError.collectAsState()
    val folders by libraryRepository.folders.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var cancelling by remember { mutableStateOf<DownloadItem?>(null) }

    fun inLibrary(path: String): Boolean {
        val dir = File(path).absoluteFile.normalize()
        return folders.any { dir.startsWith(File(it.path)) }
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
                    Text("Downloads", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        placeholder = { Text("Paste a magnet link") },
                        modifier = Modifier.weight(1f).widthIn(max = 720.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            onAddMagnet(pasted.trim())
                            pasted = ""
                        },
                        enabled = Magnet.isMagnet(pasted),
                        modifier = Modifier.focusRing(ButtonDefaults.shape),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Download", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            if (MagnetHandler.isSupported) {
                item { DefaultHandlerCard() }
            }

            startError?.let { error ->
                item {
                    Banner("The downloader couldn't start on this computer: $error")
                }
            }

            item { SectionHeader("Downloading", downloads.size) }
            if (downloads.isEmpty()) {
                item {
                    Text(
                        "Nothing downloading. Click a magnet link in your browser or paste one above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(downloads, key = { "active-${it.id}" }) { item ->
                ActiveDownloadRow(
                    item = item,
                    onPause = { scope.launch(Dispatchers.IO) { engine.pause(item.id) } },
                    onResume = { scope.launch(Dispatchers.IO) { engine.resume(item.id) } },
                    onCancel = { cancelling = item },
                )
            }

            if (finished.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Finished", finished.size, Modifier.weight(1f))
                        TextButton(onClick = { engine.clearFinished() }) { Text("Clear list") }
                    }
                }
                items(finished, key = { "done-${it.id}" }) { done ->
                    FinishedDownloadRow(
                        done = done,
                        isInLibrary = inLibrary(done.savePath),
                        onShow = { revealInFileManager(File(done.savePath, done.name).path) },
                        onAddToLibrary = { scope.launch { libraryRepository.addFolder(File(done.savePath)) } },
                        onRemove = { engine.removeFinished(done.id) },
                    )
                }
            }
        }
    }

    cancelling?.let { item ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this download?") },
            text = { Text("\"${item.name}\" will stop downloading. What's downloaded so far can be deleted or left in place.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { engine.cancel(item.id, deleteFiles = true) }
                    cancelling = null
                }) { Text("Delete files") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { cancelling = null }) { Text("Keep downloading") }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) { engine.cancel(item.id, deleteFiles = false) }
                        cancelling = null
                    }) { Text("Keep files") }
                }
            },
        )
    }
}

/**
 * Asks where to save a torrent before it starts. Shown for every magnet link, whether it was
 * clicked in a browser or pasted in.
 */
@Composable
fun AddTorrentDialog(magnet: String, initialDir: File, onStart: (File) -> Unit, onDismiss: () -> Unit) {
    var dir by remember(magnet) { mutableStateOf(initialDir) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download torrent") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    Magnet.displayName(magnet) ?: "Unnamed torrent (the name appears once it starts)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Save to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        dir.path,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    FilledTonalButton(onClick = {
                        chooseFolder(title = "Choose where to save the download", startIn = dir)?.let { dir = it }
                    }) { Text("Change…") }
                }
                Text(
                    "The torrent is removed as soon as it finishes; the files stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onStart(dir) }) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DefaultHandlerCard() {
    val scope = rememberCoroutineScope()
    var isDefault by remember { mutableStateOf<Boolean?>(null) }
    var askedWindows by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isDefault = withContext(Dispatchers.IO) { MagnetHandler.isDefault() } }
    if (isDefault != false) return

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Open magnet links with VideoPlayerSJ", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (askedWindows) {
                        "Windows wants you to confirm: in Default apps, set MAGNET to VideoPlayerSJ."
                    } else {
                        "Clicking a magnet link in your browser will bring it straight here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = {
                scope.launch {
                    val done = withContext(Dispatchers.IO) { MagnetHandler.register() }
                    if (done) isDefault = true else askedWindows = true
                }
            }) { Text(if (askedWindows) "Check again" else "Set as default") }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Banner(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun ActiveDownloadRow(item: DownloadItem, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (item.totalBytes > 0) {
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                } else if (item.state != DownloadState.Paused && item.state != DownloadState.Error) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    DownloadFormat.summary(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == DownloadState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.savePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            val paused = item.state == DownloadState.Paused || item.state == DownloadState.Error
            TooltipIconButton(if (paused) "Resume" else "Pause", onClick = if (paused) onResume else onPause) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Resume" else "Pause")
            }
            TooltipIconButton("Cancel download", onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel download")
            }
        }
    }
}

@Composable
private fun FinishedDownloadRow(
    done: FinishedDownload,
    isInLibrary: Boolean,
    onShow: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(done.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${DownloadFormat.bytes(done.sizeBytes)} · ${done.savePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isInLibrary) {
                TooltipIconButton("Add this folder to the library", onClick = onAddToLibrary) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = "Add this folder to the library")
                }
            }
            TooltipIconButton("Show in folder", onClick = onShow) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Show in folder")
            }
            TooltipIconButton("Remove from list", onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from list")
            }
        }
    }
}
