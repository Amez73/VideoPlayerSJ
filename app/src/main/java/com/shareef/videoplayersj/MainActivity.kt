package com.shareef.videoplayersj

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shareef.videoplayersj.ui.navigation.NavGraph
import com.shareef.videoplayersj.ui.theme.VideoPlayerSJTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// AppCompatActivity (a FragmentActivity) rather than ComponentActivity: the Cast button's device
// chooser is a DialogFragment and throws unless its host activity is a FragmentActivity.
class MainActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    /** Set by the player screen so it can enter picture-in-picture when the user leaves the app.
     * Only needed below Android 12, which can auto-enter from PictureInPictureParams instead. */
    var onUserLeaveHintCallback: (() -> Unit)? = null

    private val _isInPipMode = MutableStateFlow(false)
    /** Whether the activity is currently a picture-in-picture window, so the player can strip its
     * UI down to just the video. */
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintCallback?.invoke()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }

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
