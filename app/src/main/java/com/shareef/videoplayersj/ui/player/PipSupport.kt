package com.shareef.videoplayersj.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational

const val ACTION_PIP_TOGGLE_PLAY = "com.shareef.videoplayersj.PIP_TOGGLE_PLAY"
private const val PIP_REQUEST_CODE = 1

// Android rejects params outside roughly 1:2.39..2.39:1 with an IllegalArgumentException, so
// anything unusual (or an unknown 0x0 size before the first frame) is clamped into range.
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2.0f
private const val DEFAULT_ASPECT = 16f / 9f

fun isPipSupported(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

fun buildPipParams(
    context: Context,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    autoEnter: Boolean,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(aspectRatioOf(videoWidth, videoHeight))
        .setActions(listOf(playPauseAction(context, isPlaying)))

    // Android 12+ can enter PiP by itself when the user leaves; older versions are driven
    // manually from Activity.onUserLeaveHint.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
    }
    return builder.build()
}

private fun aspectRatioOf(width: Int, height: Int): Rational {
    val raw = if (width > 0 && height > 0) width.toFloat() / height else DEFAULT_ASPECT
    val clamped = raw.coerceIn(MIN_ASPECT, MAX_ASPECT)
    return Rational((clamped * 1000).toInt(), 1000)
}

private fun playPauseAction(context: Context, isPlaying: Boolean): RemoteAction {
    val intent = Intent(ACTION_PIP_TOGGLE_PLAY).setPackage(context.packageName)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        PIP_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    val label = if (isPlaying) "Pause" else "Play"
    return RemoteAction(Icon.createWithResource(context, iconRes), label, label, pendingIntent)
}
