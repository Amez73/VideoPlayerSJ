package com.shareef.videoplayersj.di

import android.content.Context
import androidx.room.Room
import com.shareef.videoplayersj.data.local.db.AppDatabase
import com.shareef.videoplayersj.data.repository.FolderRepository
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.data.repository.WatchProgressRepository
import com.shareef.videoplayersj.playback.PlaybackConnection

/** Single-module manual DI: this app is small enough that Hilt's build overhead isn't worth it. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "videoplayersj.db",
    ).build()

    val folderRepository = FolderRepository(appContext, database.libraryFolderDao())

    val libraryRepository = LibraryRepository(
        appContext,
        database.libraryFolderDao(),
        database.showDao(),
        database.videoDao(),
    )

    val watchProgressRepository = WatchProgressRepository(
        database.watchProgressDao(),
        database.videoDao(),
    )

    val playbackConnection = PlaybackConnection(appContext, watchProgressRepository)
}
