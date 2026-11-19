package com.gugaart.ui.input

import androidx.compose.ui.input.pointer.PointerEvent

/** Jetpack Compose 不报是哪个键变了；安卓上鼠标后退键系统本来就转成返回键（走 onKeyEvent 的 Key.Back）。 */
internal actual fun PointerEvent.isBackButtonRelease(): Boolean = false
