package com.shareef.videoplayersj.desktop.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FoldersDialog(repository: LibraryRepository, onAddFolder: () -> Unit, onDismiss: () -> Unit) {
    val folders by repository.folders.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library folders") },
        text = {
            Column(Modifier.width(520.dp)) {
                if (folders.isEmpty()) {
                    Text("No folders added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                folders.forEach { folder ->
                    val reachable = File(folder.path).isDirectory
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Icon(
                            if (reachable) Icons.Outlined.Folder else Icons.Outlined.FolderOff,
                            contentDescription = null,
                            tint = if (reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(folder.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (reachable) folder.path else "${folder.path} — not reachable, kept as-is",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { scope.launch { repository.removeFolder(folder.path) } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${folder.displayName}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = onAddFolder) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add folder", modifier = Modifier.padding(start = 6.dp))
            }
        },
    )
}
