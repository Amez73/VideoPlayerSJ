package com.shareef.videoplayersj.desktop.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.shareef.videoplayersj.desktop.data.AppDirs
import com.shareef.videoplayersj.desktop.playback.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import java.io.File
import kotlin.math.roundToInt

private const val THUMB_WIDTH_PX = 480
private const val THUMB_QUALITY = 82
private const val MEMORY_CACHE_SIZE = 400

/**
 * Produces a representative frame for each video, cached in memory for scrolling and as a JPEG on
 * disk so it survives restarts. Generation is serialised through one hidden VLC player, so a
 * freshly added library fills in a thumbnail at a time rather than decoding dozens of files at once.
 */
class ThumbnailLoader(
    private val factory: MediaPlayerFactory?,
    /** Called with the length VLC found while seeking, so lists can show it before first play. */
    private val onDurationDiscovered: suspend (videoId: Long, durationMs: Long) -> Unit,
) {
    private val memoryCache = object : LinkedHashMap<Long, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>) =
            size > MEMORY_CACHE_SIZE
    }

    // Files VLC couldn't decode; retrying them on every scroll would stall the queue for nothing.
    private val failed = HashSet<Long>()
    private val generationLock = Mutex()
    private var grabber: FrameGrabber? = null

    fun cached(videoId: Long): ImageBitmap? = synchronized(memoryCache) { memoryCache[videoId] }

    suspend fun load(videoId: Long, path: String): ImageBitmap? {
        cached(videoId)?.let { return it }

        return withContext(Dispatchers.IO) {
            val cacheFile = File(AppDirs.thumbnailDir, "$videoId.jpg")
            readFromDisk(cacheFile)?.let { return@withContext remember(videoId, it) }
            if (factory == null || synchronized(failed) { videoId in failed }) return@withContext null

            generationLock.withLock {
                // Another caller may have produced it while this one waited for the lock.
                cached(videoId)?.let { return@withLock it }
                readFromDisk(cacheFile)?.let { return@withLock remember(videoId, it) }

                val grabbed = obtainGrabber(factory).grab(path)
                if (grabbed == null) {
                    synchronized(failed) { failed += videoId }
                    return@withLock null
                }
                if (grabbed.durationMs > 0) onDurationDiscovered(videoId, grabbed.durationMs)
                val jpeg = encodeThumbnail(grabbed.frame) ?: return@withLock null
                runCatching { cacheFile.writeBytes(jpeg) }
                decode(jpeg)?.let { remember(videoId, it) }
            }
        }
    }

    fun release() {
        grabber?.release()
    }

    private fun obtainGrabber(factory: MediaPlayerFactory): FrameGrabber =
        grabber ?: FrameGrabber(factory).also { grabber = it }

    private fun remember(videoId: Long, bitmap: ImageBitmap): ImageBitmap {
        synchronized(memoryCache) { memoryCache[videoId] = bitmap }
        return bitmap
    }

    private fun readFromDisk(file: File): ImageBitmap? =
        if (file.exists()) runCatching { file.readBytes() }.getOrNull()?.let(::decode) else null

    private fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    /** Scales the frame to thumbnail width at its display aspect ratio and encodes it as JPEG. */
    private fun encodeThumbnail(frame: VideoFrame): ByteArray? = runCatching {
        val source = Image.makeRaster(
            ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
            frame.pixels,
            frame.width * 4,
        )
        val height = (THUMB_WIDTH_PX / frame.aspectRatio).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(THUMB_WIDTH_PX, height)
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(frame.width.toFloat(), frame.height.toFloat()),
            Rect.makeWH(THUMB_WIDTH_PX.toFloat(), height.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            null,
            true,
        )
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, THUMB_QUALITY)?.bytes
        source.close()
        surface.close()
        bytes
    }.getOrNull()
}
