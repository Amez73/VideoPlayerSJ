package com.shareef.videoplayersj.di

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.room.Room
import com.shareef.videoplayersj.data.local.db.AppDatabase
import com.shareef.videoplayersj.data.repository.FolderRepository
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.data.repository.WatchProgressRepository
import com.shareef.videoplayersj.downloads.DownloadService
import com.shareef.videoplayersj.downloads.StorageAccess
import com.shareef.videoplayersj.playback.PlaybackConnection
import com.shareef.videoplayersj.torrent.TorrentEngine
import com.shareef.videoplayersj.transfer.LocalTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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

    /** How this phone appears to the computer when sending files. */
    val deviceName: String =
        Settings.Global.getString(appContext.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** Sends files to and from the desktop app on the same Wi-Fi. */
    val localTransfer = LocalTransfer(
        deviceId = downloadPrefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            downloadPrefs.edit().putString("device_id", it).apply()
        },
        deviceName = deviceName,
        platform = "android",
        scope = scope,
    )

    // Many phones drop Wi-Fi broadcasts to save battery unless an app holds this.
    private val multicastLock = (appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("VideoPlayerSJ send files")
        .apply { setReferenceCounted(false) }

    init {
        scope.launch {
            torrentEngine.completions.collect { done ->
                DownloadService.notifyFinished(appContext, done)
                rescanIfInLibrary(File(done.savePath))
            }
        }
        scope.launch {
            localTransfer.received.collect { received -> rescanIfInLibrary(File(received.dir)) }
        }
    }

    /** Finished downloads and received files inside a library folder show up in the library straight away. */
    private suspend fun rescanIfInLibrary(dir: File) {
        val saved = dir.absoluteFile
        val inLibrary = folderRepository.getFolders().first().any { folder ->
            StorageAccess.pathForTree(Uri.parse(folder.treeUriString))?.let { saved.startsWith(it) } == true
        }
        if (inLibrary) libraryRepository.rescanAll()
    }

    /** Visible to the computer while the app is on screen; transfers already running carry on. */
    fun setVisibleForTransfers(visible: Boolean) {
        // Off the main thread, which Android won't let open sockets; one at a time, so a quick
        // stop and start can't land out of order.
        scope.launch(transferControl) {
            if (visible) {
                runCatching { multicastLock.acquire() }
                localTransfer.start()
            } else {
                localTransfer.stop()
                runCatching { multicastLock.release() }
            }
        }
    }

    private val transferControl = Dispatchers.IO.limitedParallelism(1)

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
