package com.shareef.videoplayersj.ui.common

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shareef.videoplayersj.downloads.StorageAccess
import java.io.File

/** Whether the app may write into the user's folders, re-checked on coming back from Settings. */
@Composable
fun rememberHasFileAccess(): MutableState<Boolean> {
    val context = LocalContext.current
    val hasAccess = remember { mutableStateOf(StorageAccess.hasFileAccess(context)) }
    LifecycleResumeEffect(Unit) {
        hasAccess.value = StorageAccess.hasFileAccess(context)
        onPauseOrDispose { }
    }
    return hasAccess
}

/**
 * "Save to" with the chosen folder and a button to change it, plus a way to grant file access
 * when it's missing. Folders are real paths, since downloads and received files are written
 * directly rather than through Android's document APIs.
 */
@Composable
fun SaveFolderPicker(
    dir: File?,
    onDirChange: (dir: File, treeUri: Uri) -> Unit,
    hasAccess: MutableState<Boolean>,
    purpose: String,
    label: String = "Save to",
) {
    val context = LocalContext.current
    var pickError by remember { mutableStateOf<String?>(null) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAccess.value = granted
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = StorageAccess.pathForTree(uri)
        if (picked == null) {
            pickError = "That folder isn't on this device's storage. Pick one on the phone or SD card."
        } else {
            onDirChange(picked, uri)
            pickError = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                dir?.path ?: "No folder chosen",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }
        FilledTonalButton(onClick = { folderPicker.launch(dir?.let(StorageAccess::treeUriFor)) }) {
            Text(if (dir == null) "Choose folder" else "Change folder")
        }
        pickError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!hasAccess.value) {
            Text(
                "To save $purpose into your folders, VideoPlayerSJ needs access to manage files.",
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
    }
}
