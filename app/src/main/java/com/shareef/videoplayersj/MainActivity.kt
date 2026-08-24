package com.shareef.videoplayersj

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shareef.videoplayersj.ui.navigation.NavGraph
import com.shareef.videoplayersj.ui.theme.VideoPlayerSJTheme

// AppCompatActivity (a FragmentActivity) rather than ComponentActivity: the Cast button's device
// chooser is a DialogFragment and throws unless its host activity is a FragmentActivity.
class MainActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val appContainer = (application as VideoPlayerApp).appContainer
        setContent {
            VideoPlayerSJTheme {
                NavGraph(appContainer = appContainer)
            }
        }
    }

    // Without this, the playback notification (and the lock-screen media controls that ride on
    // it) silently never appears on Android 13+ — POST_NOTIFICATIONS must be granted at runtime,
    // declaring it in the manifest alone isn't enough.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
