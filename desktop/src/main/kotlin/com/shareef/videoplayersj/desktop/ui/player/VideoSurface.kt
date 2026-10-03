package com.shareef.videoplayersj.desktop.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import com.shareef.videoplayersj.desktop.playback.PlayerController
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

/** Draws the controller's decoded frames, letterboxed to fit while keeping the video's aspect ratio. */
@Composable
fun VideoSurface(controller: PlayerController, modifier: Modifier = Modifier) {
    var image by remember { mutableStateOf<Image?>(null) }
    var aspectRatio by remember { mutableStateOf(16f / 9f) }

    LaunchedEffect(controller) {
        controller.frames.collect {
            val frame = controller.frameSink.takeLatest() ?: return@collect
            // makeRaster copies the pixels, so the array can go straight back to the pool.
            val next = Image.makeRaster(
                ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                frame.pixels,
                frame.width * 4,
            )
            controller.frameSink.recycle(frame)
            val previous = image
            image = next
            aspectRatio = frame.aspectRatio
            previous?.close()
        }
    }

    DisposableEffect(controller) {
        onDispose { image?.close() }
    }

    Canvas(modifier) {
        val frame = image ?: return@Canvas
        val canvasAspect = size.width / size.height
        val (drawWidth, drawHeight) = if (canvasAspect > aspectRatio) {
            size.height * aspectRatio to size.height
        } else {
            size.width to size.width / aspectRatio
        }
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                frame,
                Rect.makeWH(frame.width.toFloat(), frame.height.toFloat()),
                Rect.makeXYWH(left, top, drawWidth, drawHeight),
                sampling,
                null,
                true,
            )
        }
    }
}
