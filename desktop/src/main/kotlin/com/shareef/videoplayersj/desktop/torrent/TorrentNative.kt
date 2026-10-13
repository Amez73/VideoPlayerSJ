package com.shareef.videoplayersj.desktop.torrent

import com.shareef.videoplayersj.desktop.data.AppDirs
import java.io.File

/**
 * libtorrent4j ships its native library inside a jar, but can only load it from a real file.
 * Copies it out next to the app's data once (again only when the bundled build changes) and
 * points libtorrent4j at it.
 */
object TorrentNative {

    fun prepare() {
        if (System.getProperty("libtorrent4j.jni.path") != null) return
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val resource = when {
            os.startsWith("windows") -> "lib/x86_64/libtorrent4j.dll"
            os.contains("mac") -> "lib/arm64/libtorrent4j.dylib"
            arch == "arm" || arch.startsWith("armv7") -> "lib/arm/libtorrent4j.so"
            else -> "lib/x86_64/libtorrent4j.so"
        }
        val url = javaClass.classLoader.getResource(resource) ?: return
        val connection = url.openConnection()
        val size = connection.contentLengthLong
        val target = File(File(AppDirs.dataDir, "native"), resource.substringAfterLast('/'))
        if (!target.isFile || target.length() != size) {
            target.parentFile.mkdirs()
            val partial = File(target.parentFile, target.name + ".part")
            connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            target.delete()
            partial.renameTo(target)
        }
        System.setProperty("libtorrent4j.jni.path", target.absolutePath)
    }
}
