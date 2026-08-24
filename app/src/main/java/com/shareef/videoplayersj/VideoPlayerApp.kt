package com.shareef.videoplayersj

import android.app.Application
import com.shareef.videoplayersj.di.AppContainer

class VideoPlayerApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
