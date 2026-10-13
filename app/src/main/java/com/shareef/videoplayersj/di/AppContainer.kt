package com.shareef.videoplayersj.di

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.shareef.videoplayersj.data.local.db.AppDatabase
import com.shareef.videoplayersj.data.repository.FolderRepository
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.data.repository.WatchProgressRepository
import com.shareef.videoplayersj.downloads.DownloadService
import com.shareef.videoplayersj.downloads.StorageAccess
import com.shareef.videoplayersj.playback.PlaybackConnection
import com.shareef.videoplayersj.torrent.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Loaded lazily: libtorrent only starts once there's something to download. */
    val torrentEngine = TorrentEngine(File(appContext.filesDir, "torrents"), scope)

    private val downloadPrefs = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    var lastDownloadDir: File
        get() = downloadPrefs.getString("last_dir", null)?.let(::File) ?: StorageAccess.defaultDownloadDir()
        set(value) = downloadPrefs.edit().putString("last_dir", value.absolutePath).apply()

    init {
        scope.launch {
            torrentEngine.completions.collect { done ->
                DownloadService.notifyFinished(appContext, done)
                // A download that landed inside a library folder shows up in the library straight away.
                val saved = File(done.savePath).absoluteFile
                val inLibrary = folderRepository.getFolders().first().any { folder ->
                    StorageAccess.pathForTree(Uri.parse(folder.treeUriString))?.let { saved.startsWith(it) } == true
                }
                if (inLibrary) libraryRepository.rescanAll()
            }
        }
    }

    /** Picks up unfinished downloads; called from the foreground so the service is allowed to start. */
    fun resumeDownloadsIfAny() {
        if (torrentEngine.hasActiveDownloads) DownloadService.start(appContext)
    }

    fun startDownload(magnet: String, dir: File, onResult: (Result<String>) -> Unit) {
        lastDownloadDir = dir
        scope.launch(Dispatchers.IO) {
            val result = torrentEngine.add(magnet, dir)
            if (result.isSuccess) DownloadService.start(appContext)
            launch(Dispatchers.Main) { onResult(result) }
        }
    }
}
