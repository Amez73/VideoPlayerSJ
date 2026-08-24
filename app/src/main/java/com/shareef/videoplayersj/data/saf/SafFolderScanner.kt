package com.shareef.videoplayersj.data.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

data class ScannedFile(
    val documentUri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

object SafFolderScanner {

    /** Iterative (not recursive) tree walk so a deeply nested folder can't blow the call stack. */
    suspend fun scan(context: Context, treeUri: Uri): List<ScannedFile> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val results = mutableListOf<ScannedFile>()
        val stack = ArrayDeque<DocumentFile>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val current = stack.pop()
            for (child in current.listFiles()) {
                if (child.isDirectory) {
                    stack.push(child)
                } else {
                    val name = child.name ?: continue
                    if (VideoFileFilter.isVideoFile(name, child.type)) {
                        results += ScannedFile(
                            documentUri = child.uri,
                            displayName = name,
                            sizeBytes = child.length(),
                            lastModified = child.lastModified(),
                        )
                    }
                }
            }
        }

        results
    }

    fun displayNameForTree(context: Context, uri: Uri): String {
        return DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Folder"
    }
}
