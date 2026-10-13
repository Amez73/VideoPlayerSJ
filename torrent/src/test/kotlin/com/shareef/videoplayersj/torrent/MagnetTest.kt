package com.shareef.videoplayersj.torrent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagnetTest {

    @Test
    fun readsHexHashAndName() {
        val magnet = "magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=Some+Show+S01E01&tr=udp%3A%2F%2Ftracker"
        assertTrue(Magnet.isMagnet(magnet))
        assertEquals("0123456789abcdef0123456789abcdef01234567", Magnet.infoHash(magnet))
        assertEquals("Some Show S01E01", Magnet.displayName(magnet))
    }

    @Test
    fun readsBase32Hash() {
        // 32 base32 "A"s are 20 zero bytes.
        assertEquals("0".repeat(40), Magnet.infoHash("magnet:?xt=urn:btih:" + "A".repeat(32)))
    }

    @Test
    fun rejectsOtherLinks() {
        assertFalse(Magnet.isMagnet("https://example.com/file.torrent"))
        assertFalse(Magnet.isMagnet("magnet:?dn=no-hash"))
        assertNull(Magnet.displayName("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    }
}
