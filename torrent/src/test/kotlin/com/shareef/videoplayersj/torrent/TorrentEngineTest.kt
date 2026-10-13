package com.shareef.videoplayersj.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentEngineTest {

    private lateinit var root: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("torrent-engine-test").toFile()
        System.setProperty("libtorrent4j.jni.path", extractNativeLibrary(root).path)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun downloadsFromMagnetThenDropsTheTorrentAndKeepsTheFile() = runBlocking {
        val content = Random(42).nextBytes(3 * 1024 * 1024)
        val seedDir = File(root, "seed").apply { mkdirs() }
        val source = File(seedDir, "Some.Show.S01E01.mkv").apply { writeBytes(content) }

        val info = TorrentInfo(
            TorrentBuilder().path(source).flags(TorrentBuilder.V1_ONLY).generate().entry().bencode(),
        )
        val seederPort = ServerSocket(0).use { it.localPort }
        val seeder = localSession(seederPort)
        seeder.download(info, seedDir)

        val magnet = "magnet:?xt=urn:btih:${info.infoHash().toHex()}&dn=Some.Show.S01E01.mkv&x.pe=127.0.0.1:$seederPort"
        val engine = TorrentEngine(File(root, "state"), scope)
        val downloadDir = File(root, "downloads")
        val id = engine.add(magnet, downloadDir).getOrThrow()
        assertEquals(info.infoHash().toHex(), id)
        assertEquals("Some.Show.S01E01.mkv", engine.downloads.value.single().name)

        val finished = withTimeout(60_000) { engine.completions.first() }
        assertEquals(id, finished.id)
        assertEquals(content.size.toLong(), finished.sizeBytes)
        assertContentEquals(content, File(downloadDir, "Some.Show.S01E01.mkv").readBytes())
        assertTrue(engine.downloads.value.isEmpty())
        assertEquals(listOf(id), engine.finished.value.map { it.id })

        engine.stop()
        seeder.stop()

        // Nothing is left to resume, and the history survives a restart.
        val reopened = TorrentEngine(File(root, "state"), scope)
        assertTrue(!reopened.hasActiveDownloads)
        assertEquals(listOf(id), reopened.finished.value.map { it.id })
    }

    @Test
    fun keepsUnfinishedDownloadsAcrossRestarts() = runBlocking {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val stateDir = File(root, "state")
        val engine = TorrentEngine(stateDir, scope)
        engine.add("magnet:?xt=urn:btih:$hash&dn=Movie", File(root, "downloads")).getOrThrow()
        engine.pause(hash)
        engine.stop()

        val reopened = TorrentEngine(stateDir, scope)
        assertTrue(reopened.hasActiveDownloads)
        val item = reopened.downloads.value.single()
        assertEquals("Movie", item.name)
        assertEquals(DownloadState.Paused, item.state)

        reopened.start()
        reopened.cancel(hash, deleteFiles = true)
        assertTrue(reopened.downloads.value.isEmpty())
        reopened.stop()
        assertTrue(!TorrentEngine(stateDir, scope).hasActiveDownloads)
    }

    @Test
    fun rejectsInvalidMagnet() {
        val engine = TorrentEngine(File(root, "state"), scope)
        assertTrue(engine.add("https://example.com", File(root, "downloads")).isFailure)
        assertTrue(!engine.hasActiveDownloads)
    }

    private fun localSession(port: Int): SessionManager {
        val settings = SettingsPack().listenInterfaces("127.0.0.1:$port")
        settings.setEnableDht(false)
        settings.setEnableLsd(false)
        // Both ends are on 127.0.0.1, which libtorrent otherwise refuses as a connection to itself.
        settings.setBoolean(settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(), true)
        return SessionManager(false).apply { start(SessionParams(settings)) }
    }

    private fun extractNativeLibrary(dir: File): File {
        val target = File(dir, "libtorrent4j.so")
        val resource = javaClass.classLoader.getResourceAsStream("lib/x86_64/libtorrent4j.so")
            ?: error("libtorrent4j-linux not on the test classpath")
        resource.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
