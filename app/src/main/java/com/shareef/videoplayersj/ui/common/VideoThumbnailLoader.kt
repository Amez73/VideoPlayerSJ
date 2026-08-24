package com.shareef.videoplayersj.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMB_DIR = "video-thumbs"
private const val THUMB_WIDTH_PX = 320
private const val THUMB_QUALITY = 82

/** 15% in, to skip black frames, studio logos and fade-ins that open most videos. */
private const val FRAME_POSITION_FRACTION = 0.15

/**
 * Decodes a representative frame from a video and caches it, in memory for scrolling and on disk
 * so it survives restarts.
 *
 * Uses [MediaMetadataRetriever] directly rather than an image-loading library's video support:
 * those key off the MIME type, and SAF providers routinely report `application/octet-stream` for
 * containers like `.mkv`, which would silently leave much of a TV library without thumbnails.
 * `setDataSource(context, uri)` has no such restriction and never copies the file.
 */
object VideoThumbnailLoader {

    // A quarter of the app's available heap; each ~320px thumb is a few hundred KB at most.
    private val memoryCache = object : LruCache<Long, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt().coerceAtLeast(4 * 1024),
    ) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    // Serialises generation so a fling that requests the same frame repeatedly decodes it once.
    private val generationLock = Mutex()

    suspend fun load(context: Context, videoId: Long, uriString: String): Bitmap? {
        memoryCache.get(videoId)?.let { return it }

        return withContext(Dispatchers.IO) {
            val cacheFile = cacheFileFor(context, videoId)
            readFromDisk(cacheFile)?.let { cached ->
                memoryCache.put(videoId, cached)
                return@withContext cached
            }

            generationLock.withLock {
                // Another caller may have produced it while this one waited for the lock.
                memoryCache.get(videoId)?.let { return@withLock it }
                readFromDisk(cacheFile)?.let { cached ->
                    memoryCache.put(videoId, cached)
                    return@withLock cached
                }

                val bitmap = extractFrame(context, Uri.parse(uriString)) ?: return@withLock null
                memoryCache.put(videoId, bitmap)
                writeToDisk(cacheFile, bitmap)
                bitmap
            }
        }
    }

    private fun cacheFileFor(context: Context, videoId: Long): File =
        File(File(context.cacheDir, THUMB_DIR).apply { mkdirs() }, "$videoId.jpg")

    private fun readFromDisk(file: File): Bitmap? =
        if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null

    private fun writeToDisk(file: File, bitmap: Bitmap) {
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
        }.onFailure { file.delete() }
    }

    private fun extractFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frameUs = (durationMs * FRAME_POSITION_FRACTION * 1000).toLong().coerceAtLeast(0L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Decodes straight to the display size instead of allocating a full-resolution frame.
                val height = (THUMB_WIDTH_PX * 9) / 16
                retriever.getScaledFrameAtTime(
                    frameUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMB_WIDTH_PX,
                    height,
                )
            } else {
                retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { full ->
                        val scaled = scaleToWidth(full, THUMB_WIDTH_PX)
                        if (scaled !== full) full.recycle()
                        scaled
                    }
            }
        } catch (e: Exception) {
            // Unreadable file, unsupported container (avi/wmv often), or a revoked SAF permission —
            // a missing thumbnail is not worth surfacing as an error.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        if (source.width <= targetWidth || source.width == 0) return source
        val targetHeight = (source.height.toFloat() * targetWidth / source.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
