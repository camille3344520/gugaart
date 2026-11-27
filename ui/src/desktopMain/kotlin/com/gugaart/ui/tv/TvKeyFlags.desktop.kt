package com.gugaart.ui.tv

import androidx.compose.ui.input.key.KeyEvent

internal var desktopRepeatOrLongPressTestHook: ((KeyEvent) -> Boolean)? = null

internal actual fun KeyEvent.isRepeatOrLongPress(): Boolean =
    desktopRepeatOrLongPressTestHook?.invoke(this) == true
