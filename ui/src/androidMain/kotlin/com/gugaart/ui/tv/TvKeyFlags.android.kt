package com.gugaart.ui.tv

import androidx.compose.ui.input.key.KeyEvent

internal actual fun KeyEvent.isRepeatOrLongPress(): Boolean {
    val n = nativeKeyEvent
    return n.repeatCount >= 1 || (n.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
}
