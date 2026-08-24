package com.shareef.videoplayersj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shareef.videoplayersj.ui.navigation.NavGraph
import com.shareef.videoplayersj.ui.theme.VideoPlayerSJTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as VideoPlayerApp).appContainer
        setContent {
            VideoPlayerSJTheme {
                NavGraph(appContainer = appContainer)
            }
        }
    }
}
