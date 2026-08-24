package com.shareef.videoplayersj.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey val treeUriString: String,
    val displayName: String,
    val addedAt: Long,
    val lastScannedAt: Long?,
)
