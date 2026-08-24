package com.shareef.videoplayersj.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shareef.videoplayersj.data.local.db.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: LibraryFolderEntity)

    @Delete
    suspend fun delete(folder: LibraryFolderEntity)

    @Query("SELECT * FROM library_folders ORDER BY addedAt")
    fun getAll(): Flow<List<LibraryFolderEntity>>

    @Query("UPDATE library_folders SET lastScannedAt = :timestamp WHERE treeUriString = :treeUriString")
    suspend fun updateLastScanned(treeUriString: String, timestamp: Long)
}
