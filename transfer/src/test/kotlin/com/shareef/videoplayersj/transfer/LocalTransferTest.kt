package com.shareef.videoplayersj.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalTransferTest {

    private lateinit var root: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var phone: LocalTransfer
    private lateinit var pc: LocalTransfer

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("transfer-test").toFile()
        // Separate discovery ports keep the two from depending on broadcast in the test machine.
        phone = LocalTransfer("phone-id", "Phone", "android", scope, discoveryPort = 0).apply { start() }
        pc = LocalTransfer("pc-id", "PC", "windows", scope, discoveryPort = 0).apply { start() }
    }

    @AfterTest
    fun tearDown() {
        phone.stop()
        pc.stop()
        scope.cancel()
        root.deleteRecursively()
    }

    private fun pcAsPeer() = Peer("pc-id", "PC", "windows", "127.0.0.1", pc.port!!, System.currentTimeMillis())

    private fun outgoing(name: String, bytes: ByteArray) = OutgoingFile(name, bytes.size.toLong()) { bytes.inputStream() }

    @Test
    fun sendsFilesOnceAccepted() = runBlocking {
        val first = Random(1).nextBytes(5 * 1024 * 1024)
        val second = Random(2).nextBytes(1234)
        val saveDir = File(root, "inbox").apply { mkdirs() }
        File(saveDir, "Episode.mkv").writeText("already here")

        phone.send(pcAsPeer(), listOf(outgoing("Episode.mkv", first), outgoing("../../evil/notes.txt", second)))

        val offer = withTimeout(10_000) { pc.offers.first { it.isNotEmpty() }.single() }
        assertEquals("Phone", offer.fromName)
        assertEquals(first.size.toLong() + second.size, offer.totalBytes)
        pc.accept(offer.id, saveDir)

        val received = withTimeout(20_000) { pc.received.first() }
        assertEquals(listOf("Episode (2).mkv", "notes.txt"), received.names)
        assertContentEquals(first, File(saveDir, "Episode (2).mkv").readBytes())
        assertContentEquals(second, File(saveDir, "notes.txt").readBytes())
        assertEquals("already here", File(saveDir, "Episode.mkv").readText())

        val sent = withTimeout(10_000) { phone.transfers.first { it.single().state == TransferState.Done }.single() }
        assertEquals(sent.totalBytes, sent.bytesDone)
        assertTrue(pc.transfers.value.single().state == TransferState.Done)
    }

    @Test
    fun declinedOffersSendNothing() = runBlocking {
        phone.send(pcAsPeer(), listOf(outgoing("Movie.mp4", ByteArray(1000))))
        val offer = withTimeout(10_000) { pc.offers.first { it.isNotEmpty() }.single() }
        pc.decline(offer.id)

        val transfer = withTimeout(10_000) { phone.transfers.first { it.single().state == TransferState.Declined }.single() }
        assertEquals(0, transfer.bytesDone)
        assertTrue(pc.transfers.value.isEmpty())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cleansFileNames() {
        assertEquals("notes.txt", LocalTransfer.safeFileName("../../notes.txt"))
        assertEquals("a.mkv", LocalTransfer.safeFileName("C:\\Users\\x\\a.mkv"))
        assertEquals("whatisthis.mp4", LocalTransfer.safeFileName("what<is>this?.mp4"))
        assertEquals("file", LocalTransfer.safeFileName(".."))
    }
}
