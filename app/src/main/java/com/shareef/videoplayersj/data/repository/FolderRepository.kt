package com.shareef.videoplayersj.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shareef.videoplayersj.data.local.db.dao.LibraryFolderDao
import com.shareef.videoplayersj.data.local.db.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(
    private val context: Context,
    private val libraryFolderDao: LibraryFolderDao,
) {
    fun getFolders(): Flow<List<LibraryFolderEntity>> = libraryFolderDao.getAll()

    suspend fun addFolder(treeUri: Uri, displayName: String) {
        // Must happen before persisting the URI: this is what makes the grant survive restarts.
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        libraryFolderDao.insert(
            LibraryFolderEntity(
                treeUriString = treeUri.toString(),
                displayName = displayName,
                addedAt = System.currentTimeMillis(),
                lastScannedAt = null,
            ),
        )
    }

    suspend fun removeFolder(folder: LibraryFolderEntity) {
        libraryFolderDao.delete(folder)
    }
}
