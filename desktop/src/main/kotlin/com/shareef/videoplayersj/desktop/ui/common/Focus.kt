package com.shareef.videoplayersj.desktop.ui.common

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.input.InputMode

/** Outlines the element while it has keyboard/controller focus. */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused && !InputMode.isPointer) {
        // Contrasts with the page rather than the accent, since filled buttons are the accent color.
        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, shape)
    } else {
        Modifier
    }
    this.onFocusChanged { focused = it.hasFocus }.then(ring)
}

/** Moves focus with the arrow keys for any arrow press the focused element didn't use itself. */
fun Modifier.arrowKeyFocus(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            else -> return@onKeyEvent false
        }
        focusManager.moveFocus(direction)
        true
    }
}

/** The keyboard way to open a context menu: the context-menu key or Shift+F10. */
fun KeyEvent.isOptionsKey(): Boolean =
    type == KeyEventType.KeyDown &&
        (key.nativeKeyCode == java.awt.event.KeyEvent.VK_CONTEXT_MENU || (key == Key.F10 && isShiftPressed))

/**
 * The right-click menu items, shown as a dropdown when opened from the keyboard or a
 * controller, which can't reach [androidx.compose.foundation.ContextMenuArea].
 */
@Composable
fun OptionsDropdown(
    expanded: Boolean,
    items: List<ContextMenuItem>,
    onDismiss: () -> Unit,
    returnFocusTo: FocusRequester,
) {
    val firstItem = remember { FocusRequester() }
    // Hands focus back to the element the menu was opened from, so navigation carries on there.
    val dismiss = {
        onDismiss()
        runCatching { returnFocusTo.requestFocus() }
        Unit
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = dismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || (event.key != Key.Escape && event.key != Key.Backspace)) {
                return@onPreviewKeyEvent false
            }
            dismiss()
            true
        },
    ) {
        items.forEachIndexed { index, item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = {
                    dismiss()
                    item.onClick()
                },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstItem) else Modifier)
                    .focusRing(RectangleShape),
            )
        }
        // Starts with the first item selected, so a controller can act on it straight away.
        LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
    }
}
