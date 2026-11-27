package com.gugaart.ui.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * 条目在服务器上变了（这一边写进去的：字幕「保存到服务器」1.5.1 subfix）：详情页 / 播放器面板的「源」状态用 [of] 当一个键，
 * [bump] 一下就重新取这一条（字幕选单里出现新存的那条）。只在界面线程上读写。
 */
object ItemRevisions {
    private val revs = mutableStateMapOf<String, Int>()

    fun of(itemId: String): Int = revs[itemId] ?: 0

    fun bump(itemId: String) {
        revs[itemId] = of(itemId) + 1
        // 刚取过的这一条（3 秒内的合并）不能再拿来用
        ItemFetch.clear()
    }
}
