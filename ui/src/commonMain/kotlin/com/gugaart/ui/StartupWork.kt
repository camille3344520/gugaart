package com.gugaart.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 冷启动恢复会话：整段不在调用者的线程上做（调用者是根上的 LaunchedEffect = 界面线程）。上游的 `SessionManager.restore()`
 * 每次都把当前服务器写回一遍：Android Keystore 加密 + SharedPreferences 提交（起一个写盘线程），
 * 1.5.1 性能批次手机 release 包冷启动采样主线程占 79 ms，正赶在首页第一帧之后、出内容之前。
 * 会话的状态都是 StateFlow、存储是线程安全的，换线程不改行为。
 */
internal suspend fun restoreSessionOffMain(seed: suspend () -> Unit, restore: suspend () -> Unit) = withContext(Dispatchers.Default) {
    seed()
    restore()
}
