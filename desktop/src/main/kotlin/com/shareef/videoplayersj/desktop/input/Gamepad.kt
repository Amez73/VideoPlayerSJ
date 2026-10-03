package com.shareef.videoplayersj.desktop.input

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GamepadButton(val repeats: Boolean = false) {
    Up(repeats = true),
    Down(repeats = true),
    Left(repeats = true),
    Right(repeats = true),
    A, B, X, Y,
    LeftBumper(repeats = true),
    RightBumper(repeats = true),
    LeftTrigger(repeats = true),
    RightTrigger(repeats = true),
    Start,
    Back,
}

private const val POLL_INTERVAL_MS = 16L
private const val RESCAN_INTERVAL_MS = 2_000L
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 110L
private const val STICK_THRESHOLD = 16_000
private const val TRIGGER_THRESHOLD = 128
private const val MAX_CONTROLLERS = 4
private const val ERROR_SUCCESS = 0

/**
 * Reads Xbox-style controllers through Windows' XInput and reports button presses, repeating
 * held directions the way a held arrow key does. Steam passes its controllers through as XInput
 * too, so this also covers PlayStation/Switch pads when the app is launched from Steam.
 * Does nothing on other platforms or when XInput can't be loaded.
 */
class Gamepad(scope: CoroutineScope) {

    private val _presses = MutableSharedFlow<GamepadButton>(extraBufferCapacity = 16)
    val presses: SharedFlow<GamepadButton> = _presses.asSharedFlow()

    private val xinput: XInput? = loadXInput()

    init {
        if (xinput != null) scope.launch(Dispatchers.IO) { pollLoop(xinput) }
    }

    private suspend fun CoroutineScope.pollLoop(xinput: XInput) {
        val state = Memory(16)
        // Querying an empty slot is slow, so unplugged slots are only rechecked every few seconds.
        val connected = BooleanArray(MAX_CONTROLLERS)
        var lastRescan = 0L
        val pressedSince = mutableMapOf<GamepadButton, Long>()
        val lastRepeat = mutableMapOf<GamepadButton, Long>()

        while (isActive) {
            val now = System.currentTimeMillis()
            val rescan = now - lastRescan >= RESCAN_INTERVAL_MS
            if (rescan) lastRescan = now

            val held = mutableSetOf<GamepadButton>()
            for (slot in 0 until MAX_CONTROLLERS) {
                if (!connected[slot] && !rescan) continue
                connected[slot] = xinput.XInputGetState(slot, state) == ERROR_SUCCESS
                if (connected[slot]) held += readButtons(state)
            }

            pressedSince.keys.retainAll(held)
            lastRepeat.keys.retainAll(held)
            for (button in held) {
                val since = pressedSince[button]
                if (since == null) {
                    pressedSince[button] = now
                    _presses.tryEmit(button)
                } else if (button.repeats && now - since >= REPEAT_DELAY_MS) {
                    if (now - (lastRepeat[button] ?: 0L) >= REPEAT_INTERVAL_MS) {
                        lastRepeat[button] = now
                        _presses.tryEmit(button)
                    }
                }
            }

            delay(if (connected.any { it }) POLL_INTERVAL_MS else RESCAN_INTERVAL_MS)
        }
    }

    // XINPUT_STATE: DWORD packet number, then XINPUT_GAMEPAD { WORD buttons; BYTE left/right
    // trigger; SHORT left X/Y, right X/Y }.
    private fun readButtons(state: Memory): Set<GamepadButton> {
        val buttons = state.getShort(4).toInt() and 0xFFFF
        val leftTrigger = state.getByte(6).toInt() and 0xFF
        val rightTrigger = state.getByte(7).toInt() and 0xFF
        val stickX = state.getShort(8).toInt()
        val stickY = state.getShort(10).toInt()

        return buildSet {
            if (buttons and 0x0001 != 0 || stickY > STICK_THRESHOLD) add(GamepadButton.Up)
            if (buttons and 0x0002 != 0 || stickY < -STICK_THRESHOLD) add(GamepadButton.Down)
            if (buttons and 0x0004 != 0 || stickX < -STICK_THRESHOLD) add(GamepadButton.Left)
            if (buttons and 0x0008 != 0 || stickX > STICK_THRESHOLD) add(GamepadButton.Right)
            if (buttons and 0x0010 != 0) add(GamepadButton.Start)
            if (buttons and 0x0020 != 0) add(GamepadButton.Back)
            if (buttons and 0x0100 != 0) add(GamepadButton.LeftBumper)
            if (buttons and 0x0200 != 0) add(GamepadButton.RightBumper)
            if (buttons and 0x1000 != 0) add(GamepadButton.A)
            if (buttons and 0x2000 != 0) add(GamepadButton.B)
            if (buttons and 0x4000 != 0) add(GamepadButton.X)
            if (buttons and 0x8000 != 0) add(GamepadButton.Y)
            if (leftTrigger > TRIGGER_THRESHOLD) add(GamepadButton.LeftTrigger)
            if (rightTrigger > TRIGGER_THRESHOLD) add(GamepadButton.RightTrigger)
        }
    }

    @Suppress("FunctionName")
    private interface XInput : Library {
        fun XInputGetState(userIndex: Int, state: Pointer): Int
    }

    private fun loadXInput(): XInput? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        // xinput1_4 ships with Windows 8+; 9_1_0 is the older one present on every version.
        for (name in listOf("xinput1_4", "xinput9_1_0")) {
            runCatching { return Native.load(name, XInput::class.java) }
        }
        return null
    }
}
