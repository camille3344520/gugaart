package com.gugaart.ui.tv

import androidx.compose.ui.input.key.KeyEvent

/**
 * 平台键事件是否已经带着「长按 / 自动重复」标记（安卓 `repeatCount >= 1` 或 `FLAG_LONG_PRESS`）。
 */
internal expect fun KeyEvent.isRepeatOrLongPress(): Boolean
