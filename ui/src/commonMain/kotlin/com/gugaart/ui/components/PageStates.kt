package com.gugaart.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.gugugaga.app.nav.NavController

/**
 * 返回栈上的每一页记住自己的可保存状态（列表 / 网格 / 滚动的位置，`rememberSaveable` 的东西）。
 * 用户 2026-11-17：「从片库进入影片后，返回后会自动回到最上面，体验很差，应该回到进入的那个进度」。
 * 以前三端都是按当前路由直接画页面：进详情时片库整页拆掉，回来重新组合，滚动位置从 0 开始；电视的「每页记住焦点」也落空
 * （记住的那张卡在懒加载网格的下面，还没排出来，焦点只好落回第一张）。
 *
 * 用法：`PageState(nav, slot) { 页面 }`。[slot]：同一个返回栈条目里还要再分的页（首页的几个标签页），0..6；7 = 不分。
 * 离开返回栈的条目，存的状态一并丢掉（不越存越多）。
 */
class PageStates(private val holder: SaveableStateHolder) {
    private val used = HashSet<Long>()

    @Composable
    fun Page(nav: NavController, slot: Int = 7, content: @Composable () -> Unit) {
        val key = nav.current.key * 8L + slot.coerceIn(0, 7)
        used += key
        holder.SaveableStateProvider(key, content)
    }

    /** 不在返回栈里的条目：忘掉它们存的状态。 */
    fun retain(stackKeys: List<Long>) {
        val alive = stackKeys.toHashSet()
        val gone = used.filter { (it / 8L) !in alive }
        for (k in gone) {
            holder.removeState(k)
            used -= k
        }
    }
}

@Composable
fun rememberPageStates(nav: NavController): PageStates {
    val holder = rememberSaveableStateHolder()
    val states = remember(holder) { PageStates(holder) }
    val stackKeys = nav.entryKeys()
    LaunchedEffect(stackKeys) { states.retain(stackKeys) }
    return states
}
