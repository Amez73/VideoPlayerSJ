package com.shareef.videoplayersj.desktop.ui.common

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

private const val WS_CAPTION = 0x00C00000
private const val WS_THICKFRAME = 0x00040000
private const val FRAME_STYLES = WS_CAPTION or WS_THICKFRAME
private const val SWP_FLAGS = WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or
    WinUser.SWP_NOACTIVATE or WinUser.SWP_FRAMECHANGED

private val isWindows = System.getProperty("os.name").startsWith("Windows")

/**
 * On Windows, Compose's fullscreen stretches the window over the screen but keeps its title bar.
 * This hides the title bar and border while fullscreen and brings them back afterwards.
 */
fun setTitleBarHidden(window: Window, hidden: Boolean) {
    if (!isWindows || !window.isDisplayable) return
    runCatching {
        val hwnd = HWND(Native.getComponentPointer(window) ?: Pointer.NULL)
        val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        val updated = if (hidden) style and FRAME_STYLES.inv() else style or FRAME_STYLES
        if (updated == style) return
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, updated)
        User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FLAGS)
    }
}
