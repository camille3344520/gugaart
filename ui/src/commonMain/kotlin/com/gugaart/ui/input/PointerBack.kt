package com.gugaart.ui.input

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * 鼠标「后退」侧键抬起了（M2-7 / B29：电脑上后退键 = 返回上一页）。
 * 哪个键变了（`PointerEvent.button` / `PointerButton.Back`）只有 Compose Multiplatform 的桌面 / iOS 报得出，安卓的 Jetpack Compose
 * 没有这个 API——所以按平台写：桌面、iOS 认后退键；安卓恒为 false（安卓上鼠标后退键系统本来就转成返回键）。
 */
internal expect fun PointerEvent.isBackButtonRelease(): Boolean
