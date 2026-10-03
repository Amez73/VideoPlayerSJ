package com.shareef.videoplayersj.desktop.input

import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Buttons by position on an Xbox-style pad: A is the bottom face button (Cross on PlayStation). */
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
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 110L
private const val STICK_THRESHOLD = 0.5f
private const val TRIGGER_THRESHOLD = 0.5f
private const val MAX_CONTROLLERS = 4

/**
 * Reads game controllers through SDL, which covers PlayStation (DualSense, DualShock 4), Xbox,
 * Switch Pro and most other pads and maps them all to the same Xbox-style layout. Reports button
 * presses, repeating held directions the way a held arrow key does. Does nothing if SDL can't be
 * loaded.
 */
class Gamepad(scope: CoroutineScope) {

    private val _presses = MutableSharedFlow<GamepadButton>(extraBufferCapacity = 16)
    val presses: SharedFlow<GamepadButton> = _presses.asSharedFlow()

    // SDL wants every call from the thread that initialized it.
    private val sdlThread = Executors.newSingleThreadExecutor { Thread(it, "gamepad").apply { isDaemon = true } }
        .asCoroutineDispatcher()

    init {
        scope.launch(sdlThread) {
            val manager = runCatching { ControllerManager().apply { initSDLGamepad() } }.getOrNull()
                ?: return@launch
            try {
                pollLoop(manager)
            } finally {
                manager.quitSDLGamepad()
            }
        }
    }

    private suspend fun CoroutineScope.pollLoop(manager: ControllerManager) {
        val pressedSince = mutableMapOf<GamepadButton, Long>()
        val lastRepeat = mutableMapOf<GamepadButton, Long>()

        while (isActive) {
            manager.update()
            val now = System.currentTimeMillis()
            val held = mutableSetOf<GamepadButton>()
            for (index in 0 until MAX_CONTROLLERS) {
                val state = manager.getState(index)
                if (state.isConnected) held += heldButtons(state)
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

            delay(POLL_INTERVAL_MS)
        }
    }

    // Stick Y comes straight from SDL, where positive is down.
    private fun heldButtons(state: ControllerState): Set<GamepadButton> = buildSet {
        if (state.dpadUp || state.leftStickY < -STICK_THRESHOLD) add(GamepadButton.Up)
        if (state.dpadDown || state.leftStickY > STICK_THRESHOLD) add(GamepadButton.Down)
        if (state.dpadLeft || state.leftStickX < -STICK_THRESHOLD) add(GamepadButton.Left)
        if (state.dpadRight || state.leftStickX > STICK_THRESHOLD) add(GamepadButton.Right)
        if (state.a) add(GamepadButton.A)
        if (state.b) add(GamepadButton.B)
        if (state.x) add(GamepadButton.X)
        if (state.y) add(GamepadButton.Y)
        if (state.lb) add(GamepadButton.LeftBumper)
        if (state.rb) add(GamepadButton.RightBumper)
        if (state.leftTrigger > TRIGGER_THRESHOLD) add(GamepadButton.LeftTrigger)
        if (state.rightTrigger > TRIGGER_THRESHOLD) add(GamepadButton.RightTrigger)
        if (state.start) add(GamepadButton.Start)
        if (state.back) add(GamepadButton.Back)
    }
}
