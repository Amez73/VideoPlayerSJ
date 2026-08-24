package com.shareef.videoplayersj.playback.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Targets Google's stock Default Media Receiver — no custom receiver app/registration needed. */
class VideoPlayerCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setResumeSavedSession(false)
            .setEnableReconnectionService(false)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
