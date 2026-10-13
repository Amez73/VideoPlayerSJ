package com.shareef.videoplayersj.torrent

import java.net.URLDecoder

/**
 * Reads the parts of a magnet link the UI needs before the torrent engine is running: whether
 * it's a magnet link at all, and the name to show while the real metadata is fetched.
 */
object Magnet {

    fun isMagnet(text: String): Boolean = text.trim().startsWith("magnet:?", ignoreCase = true) && infoHash(text) != null

    /** The torrent's name from the link's `dn` field, if it has one. */
    fun displayName(magnet: String): String? = params(magnet)["dn"]?.firstOrNull()?.takeIf { it.isNotBlank() }

    /** The v1 info hash as 40 lowercase hex characters, accepting both hex and base32 forms. */
    fun infoHash(magnet: String): String? {
        val topics = params(magnet)["xt"] ?: return null
        for (topic in topics) {
            if (!topic.startsWith("urn:btih:", ignoreCase = true)) continue
            val hash = topic.substring("urn:btih:".length)
            when {
                hash.length == 40 && hash.all { it.isHexDigit() } -> return hash.lowercase()
                hash.length == 32 -> base32ToHex(hash)?.let { return it }
            }
        }
        return null
    }

    private fun params(magnet: String): Map<String, List<String>> {
        val query = magnet.trim().substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val value = runCatching { URLDecoder.decode(part.substringAfter('='), "UTF-8") }.getOrNull()
                    ?: return@mapNotNull null
                key.lowercase() to value
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun base32ToHex(text: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = StringBuilder()
        for (c in text.uppercase()) {
            val value = alphabet.indexOf(c).takeIf { it >= 0 } ?: return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.append("%02x".format((buffer shr bits) and 0xFF))
            }
        }
        return out.toString().takeIf { it.length == 40 }
    }
}
