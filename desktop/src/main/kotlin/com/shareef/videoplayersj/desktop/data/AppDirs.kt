package com.shareef.videoplayersj.desktop.data

import java.io.File

private const val APP_DIR_NAME = "VideoPlayerSJ"

/** Per-user locations following each OS's convention for application data. */
object AppDirs {

    val dataDir: File by lazy {
        // Lets a test or second profile run against its own library.
        System.getenv("VIDEOPLAYERSJ_DATA_DIR")?.let { return@lazy File(it).apply { mkdirs() } }
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val base = when {
            os.startsWith("windows") -> System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
            os.contains("mac") -> "$home/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        }
        File(base, APP_DIR_NAME).apply { mkdirs() }
    }

    val thumbnailDir: File by lazy { File(dataDir, "video-thumbs").apply { mkdirs() } }

    val libraryFile: File get() = File(dataDir, "library.json")
}
