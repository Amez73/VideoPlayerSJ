package com.shareef.videoplayersj.desktop.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseEvent

/**
 * Whether the user is currently driving the app with the mouse or with keys/a controller.
 * Focus highlights are only drawn in the latter case, so a mouse user doesn't see a stray
 * outline left on whatever they last clicked.
 */
object InputMode {
    var isPointer by mutableStateOf(true)
        private set

    private var lastPointerLocation: Point? = null

    // Enter/exit, and moves where the cursor didn't actually travel, also fire when the window
    // appears or goes fullscreen under a resting mouse, so they don't count.
    private fun isRealPointerActivity(event: MouseEvent): Boolean = when (event.id) {
        MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL -> true
        MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED -> {
            val location = event.locationOnScreen
            (lastPointerLocation != null && location != lastPointerLocation).also { lastPointerLocation = location }
        }
        else -> false
    }

    fun install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event -> if (event is MouseEvent && isRealPointerActivity(event)) isPointer = true },
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK,
        )
    }

    fun onKeyOrGamepad() {
        isPointer = false
    }
}
