package com.shareef.videoplayersj.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.shareef.videoplayersj.data.local.db.dao.LibraryFolderDao
import com.shareef.videoplayersj.data.local.db.dao.ShowDao
import com.shareef.videoplayersj.data.local.db.dao.VideoDao
import com.shareef.videoplayersj.data.local.db.dao.WatchProgressDao
import com.shareef.videoplayersj.data.local.db.entity.LibraryFolderEntity
import com.shareef.videoplayersj.data.local.db.entity.ShowEntity
import com.shareef.videoplayersj.data.local.db.entity.VideoEntity
import com.shareef.videoplayersj.data.local.db.entity.WatchProgressEntity

@Database(
    entities = [
        LibraryFolderEntity::class,
        ShowEntity::class,
        VideoEntity::class,
        WatchProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryFolderDao(): LibraryFolderDao
    abstract fun showDao(): ShowDao
    abstract fun videoDao(): VideoDao
    abstract fun watchProgressDao(): WatchProgressDao
}
