package com.shareef.videoplayersj.ui.downloads

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shareef.videoplayersj.data.saf.SafFolderScanner
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.downloads.StorageAccess
import com.shareef.videoplayersj.torrent.DownloadFormat
import com.shareef.videoplayersj.torrent.DownloadItem
import com.shareef.videoplayersj.torrent.DownloadState
import com.shareef.videoplayersj.torrent.FinishedDownload
import com.shareef.videoplayersj.torrent.Magnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * The torrent side of the app: what's downloading, what finished, and a box to paste a magnet
 * link into. Downloads stop and are removed as soon as they complete; nothing is seeded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(appContainer: AppContainer, onAddMagnet: (String) -> Unit, onBack: () -> Unit) {
    val engine = appContainer.torrentEngine
    val downloads by engine.downloads.collectAsState()
    val finished by engine.finished.collectAsState()
    val startError by engine.startError.collectAsState()
    val libraryDirs by remember {
        appContainer.folderRepository.getFolders().map { folders ->
            folders.mapNotNull { StorageAccess.pathForTree(Uri.parse(it.treeUriString)) }
        }
    }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pasted by remember { mutableStateOf("") }
    var cancelling by remember { mutableStateOf<DownloadItem?>(null) }

    val libraryFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                appContainer.folderRepository.addFolder(uri, SafFolderScanner.displayNameForTree(context, uri))
                appContainer.libraryRepository.rescanAll()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        placeholder = { Text("Paste a magnet link") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onAddMagnet(pasted.trim())
                            pasted = ""
                        },
                        enabled = Magnet.isMagnet(pasted),
                    ) { Text("Add") }
                }
            }

            startError?.let { error ->
                item {
                    Text(
                        "The downloader couldn't start on this device: $error",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { SectionHeader("Downloading") }
            if (downloads.isEmpty()) {
                item {
                    Text(
                        "Nothing downloading. Tap a magnet link in your browser and pick VideoPlayerSJ " +
                            "(choose \"Always\" to make it the default), or paste one above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(downloads, key = { "active-${it.id}" }) { item ->
                ActiveDownloadCard(
                    item = item,
                    onPause = { scope.launch(Dispatchers.IO) { engine.pause(item.id) } },
                    onResume = { scope.launch(Dispatchers.IO) { engine.resume(item.id) } },
                    onCancel = { cancelling = item },
                )
            }

            if (finished.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Finished", Modifier.weight(1f))
                        TextButton(onClick = { engine.clearFinished() }) { Text("Clear list") }
                    }
                }
                items(finished, key = { "done-${it.id}" }) { done ->
                    val saveDir = File(done.savePath)
                    FinishedDownloadCard(
                        done = done,
                        isInLibrary = libraryDirs.any { saveDir.absoluteFile.startsWith(it) },
                        onAddToLibrary = { libraryFolderPicker.launch(StorageAccess.treeUriFor(saveDir)) },
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
                    TextButton(onClick = { cancelling = null }) { Text("Back") }
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
 * Asks where to save a torrent before it starts, and for storage access the first time. Shown
 * for every magnet link, whether it was tapped in another app or pasted in.
 */
@Composable
fun AddTorrentDialog(magnet: String, initialDir: File, onStart: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var dir by remember(magnet) { mutableStateOf(initialDir) }
    var hasAccess by remember { mutableStateOf(StorageAccess.hasFileAccess(context)) }
    var pickError by remember { mutableStateOf<String?>(null) }

    // Coming back from the "all files access" settings page.
    LifecycleResumeEffect(Unit) {
        hasAccess = StorageAccess.hasFileAccess(context)
        onPauseOrDispose { }
    }

    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAccess = granted
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = StorageAccess.pathForTree(uri)
        if (picked == null) {
            pickError = "That folder isn't on this device's storage. Pick one on the phone or SD card."
        } else {
            dir = picked
            pickError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download torrent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Magnet.displayName(magnet) ?: "Unnamed torrent (the name appears once it starts)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Save to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        dir.path,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
                FilledTonalButton(onClick = { folderPicker.launch(StorageAccess.treeUriFor(dir)) }) { Text("Change folder") }
                pickError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!hasAccess) {
                    Text(
                        "To save downloads into your folders, VideoPlayerSJ needs access to manage files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledTonalButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching { context.startActivity(StorageAccess.fileAccessSettingsIntent(context)) }
                        } else {
                            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }) { Text("Allow file access") }
                }
                Text(
                    "The torrent is removed as soon as it finishes; the files stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onStart(dir) }, enabled = hasAccess) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 12.dp))
}

@Composable
private fun ActiveDownloadCard(item: DownloadItem, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            val paused = item.state == DownloadState.Paused || item.state == DownloadState.Error
            IconButton(onClick = if (paused) onResume else onPause) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Resume" else "Pause")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel download")
            }
        }
    }
}

@Composable
private fun FinishedDownloadCard(done: FinishedDownload, isInLibrary: Boolean, onAddToLibrary: () -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(done.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${DownloadFormat.bytes(done.sizeBytes)} · ${done.savePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isInLibrary) {
                IconButton(onClick = onAddToLibrary) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = "Add this folder to the library")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from list")
            }
        }
    }
}
