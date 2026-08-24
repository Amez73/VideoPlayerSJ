package com.shareef.videoplayersj.playback.cast

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

/**
 * Serves exactly one video at a time over HTTP so a Cast receiver (which can't resolve the
 * phone's local content:// SAF URIs) can fetch it over the LAN. Gated by a random per-session
 * token in the URL path so this is never a general file proxy — only the video most recently
 * handed to [serve] is reachable, and only via that exact token.
 */
class CastMediaServer(private val context: Context) : NanoHTTPD(0) {

    private var sessionToken: String? = null
    private var servedUri: Uri? = null
    private var servedMimeType: String = "video/mp4"
    private var servedLength: Long = -1L

    /** Starts (or repoints) the server for exactly this one video; returns the http:// URL to hand to CastPlayer. */
    fun serve(videoId: Long, uri: Uri, host: String): String {
        servedUri = uri
        servedMimeType = resolveMimeType(uri)
        servedLength = queryLength(uri)
        sessionToken = UUID.randomUUID().toString()
        if (!isAlive) start(SOCKET_READ_TIMEOUT, true)
        return "http://$host:$listeningPort/media/$sessionToken/$videoId"
    }

    /** MIME type that [serve] would advertise for [uri] — exposed so callers can hint it on the MediaItem too. */
    fun mimeTypeForUri(uri: Uri): String = resolveMimeType(uri)

    fun stopServing() {
        if (isAlive) stop()
        sessionToken = null
        servedUri = null
        servedLength = -1L
    }

    override fun serve(session: IHTTPSession): Response {
        val token = sessionToken
        val uri = servedUri
        // Anything not matching the current session's exact token is refused (never a general file proxy).
        if (token == null || uri == null || !session.uri.startsWith("/media/$token/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        }

        val total = servedLength
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        } ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")

        if (total <= 0L) {
            val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            return newChunkedResponse(Response.Status.OK, servedMimeType, stream).apply {
                addHeader("Accept-Ranges", "none")
            }
        }

        val rangeHeader = session.headers["range"]
        val (start, end) = parseRange(rangeHeader, total)
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd).apply { channel.position(start) }
        val length = end - start + 1

        val response = if (rangeHeader != null) {
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, servedMimeType, stream, length).apply {
                addHeader("Content-Range", "bytes $start-$end/$total")
            }
        } else {
            newFixedLengthResponse(Response.Status.OK, servedMimeType, stream, length)
        }
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long> {
        val lastByte = (total - 1).coerceAtLeast(0L)
        if (header == null || !header.startsWith("bytes=")) return 0L to lastByte

        val spec = header.removePrefix("bytes=").substringBefore(',')
        val parts = spec.split("-", limit = 2)
        val startPart = parts.getOrNull(0)?.trim().orEmpty()
        val endPart = parts.getOrNull(1)?.trim().orEmpty()

        return when {
            startPart.isNotEmpty() -> {
                val start = startPart.toLongOrNull()?.coerceIn(0L, lastByte) ?: 0L
                val end = endPart.toLongOrNull()?.coerceIn(start, lastByte) ?: lastByte
                start to end
            }
            endPart.isNotEmpty() -> {
                val suffixLength = endPart.toLongOrNull() ?: total
                val start = (total - suffixLength).coerceAtLeast(0L)
                start to lastByte
            }
            else -> 0L to lastByte
        }
    }

    private fun queryLength(uri: Uri): Long =
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (e: Exception) {
            -1L
        }

    private fun resolveMimeType(uri: Uri): String {
        context.contentResolver.getType(uri)
            ?.takeIf { it != "application/octet-stream" }
            ?.let { return it }
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return EXTENSION_FALLBACK[ext] ?: "application/octet-stream"
    }

    companion object {
        private val EXTENSION_FALLBACK = mapOf(
            "mkv" to "video/x-matroska",
            "avi" to "video/x-msvideo",
            "mov" to "video/quicktime",
            "ts" to "video/mp2t",
            "3gp" to "video/3gpp",
            "flv" to "video/x-flv",
            "wmv" to "video/x-ms-wmv",
        )
    }
}
