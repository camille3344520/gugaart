package com.gugaart.ui.tv.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.gugugaga.ds.focus.FocusRegistry

/** 遥控器的四个方向。 */
enum class TvDir { Left, Right, Up, Down }

/**
 * 一屏的「方向键路径」（`docs/platforms.md` §3 的路径表写成代码）。焦点本身、focusKey 的登记、把焦点交给某个 key 都是上游的
 * （`com.gugugaga.ds.focus.FocusRegistry`）；这里只描述**这一屏的路径**：
 *
 * - 一屏分成几个**组**（一行按钮、一排卡片、一列选项…），组里的成员按声明顺序排；
 * - 沿着组的方向（横排 = 左右，竖列 = 上下）按一下 = 组里前一个 / 后一个；
 * - 走到头、或者按了另一个方向 = 问这一屏的 [edge]：去哪个组（回到那个组**上次停的那一个**）、原地不动（[STAY]）、还是不管（null：交给外壳，例如最左再按左 = 进导航）。
 *
 * 为什么不直接用 Compose 的二维焦点搜索：它只认几何位置，横排走到头会跳到别的栏里碰巧在右边的卡片上，
 * 「上下换栏回到那一栏上次停的卡」「焦点到哪季就换哪季」这类路径也表达不了。和上游 `FocusRowChain` 是同一个理由。
 */
@Stable
class TvGroups {
    private class Group(val keys: List<String>, val horizontal: Boolean, val default: String?)

    private val groups = HashMap<String, Group>()
    private val last = mutableStateMapOf<String, String>()

    /** 现在真的拿着焦点的成员（不在任何组里 = null）。 */
    var current: String? by mutableStateOf(null)
        private set

    /** 现在焦点在哪个组（焦点离开这一屏的组以后保持上一次的值：版面不跟着跳）。 */
    var currentGroup: String? by mutableStateOf(null)
        private set

    /** 走到组的边上 / 按了另一个方向：去哪个组。 */
    var edge: (group: String, dir: TvDir) -> String? = { _, _ -> null }

    /**
     * 方向键先问它（在组里走动之前）：返回 true = 这一下它管了，焦点不动。「拿起来的东西跟着方向键挪」用它
     * （设置里排顺序：确定拿起 → 上下移动 → 确定放下）。平时是 null。
     */
    var intercept: ((dir: TvDir) -> Boolean)? = null

    fun set(id: String, keys: List<String>, horizontal: Boolean = true, default: String? = null) {
        groups[id] = Group(keys, horizontal, default)
    }

    fun remove(id: String) {
        groups.remove(id)
    }

    fun has(id: String): Boolean = groups[id]?.keys?.isNotEmpty() == true

    fun groupOf(key: String): String? = groups.entries.firstOrNull { key in it.value.keys }?.key

    /** 这个组上次停在哪个成员（可观察：横排据此决定谁在左起第一格）。 */
    fun lastOf(id: String): String? = last[id]

    /** 进这个组时落在哪：上次停的 → 组的默认项 → 第一个。 */
    fun entry(id: String): String? {
        val g = groups[id] ?: return null
        return last[id]?.takeIf { it in g.keys } ?: g.default?.takeIf { it in g.keys } ?: g.keys.firstOrNull()
    }

    /** 忘掉这个组上次停在哪（内容整个换了：换季以后分集排回到第一格）。 */
    fun forget(id: String) {
        last.remove(id)
    }

    fun focusChanged(key: String, focused: Boolean) {
        if (focused) {
            current = key
            groupOf(key)?.let {
                last[it] = key
                currentGroup = it
            }
        } else if (current == key) {
            current = null
        }
    }

    /** 按了一个方向键。true = 这一屏处理了（包括「原地不动」）。 */
    fun move(dir: TvDir, registry: FocusRegistry): Boolean {
        if (intercept?.invoke(dir) == true) return true
        val cur = current ?: return false
        val gid = groupOf(cur) ?: return false
        val g = groups[gid] ?: return false
        val along = if (g.horizontal) dir == TvDir.Left || dir == TvDir.Right else dir == TvDir.Up || dir == TvDir.Down
        if (along) {
            val j = g.keys.indexOf(cur) + if (dir == TvDir.Right || dir == TvDir.Down) 1 else -1
            if (j in g.keys.indices) {
                registry.request(g.keys[j])
                return true
            }
        }
        val next = edge(gid, dir) ?: return false
        if (next != STAY) entry(next)?.let { registry.request(it) }
        return true
    }

    /** 把焦点放进一个组（打开页边选项、翻到下一屏）。 */
    fun enter(id: String, registry: FocusRegistry): Boolean = entry(id)?.let { registry.request(it) } == true

    companion object {
        /** [edge] 的返回值：这个方向到头了，原地不动（不让焦点跑到别处）。 */
        const val STAY = ""
    }
}

/** 等这个组的落点挂上（内容刚换进来的那一帧它还不在），再把焦点放进去；最多等 1 秒。 */
suspend fun TvGroups.enterWhenReady(id: String, registry: FocusRegistry): Boolean {
    repeat(40) {
        if (enter(id, registry)) return true
        kotlinx.coroutines.delay(25)
    }
    return false
}

val LocalTvGroups = staticCompositionLocalOf { TvGroups() }

/** 登记一个组（每次重组都更新成员表）。 */
@Composable
fun TvGroup(id: String, keys: List<String>, horizontal: Boolean = true, default: String? = null) {
    val groups = LocalTvGroups.current
    // 组合阶段就登记：同一帧里随后的 entry() / 横排的「谁在第一格」要用
    groups.set(id, keys, horizontal, default)
    SideEffect { groups.set(id, keys, horizontal, default) }
}

/** 挂在一屏的根上：方向键先问 [groups]。 */
fun Modifier.tvGroupKeys(groups: TvGroups, registry: FocusRegistry, onOther: (Key) -> Boolean = { false }): Modifier = onPreviewKeyEvent { e ->
    val dir = when (e.key) {
        Key.DirectionLeft -> TvDir.Left
        Key.DirectionRight -> TvDir.Right
        Key.DirectionUp -> TvDir.Up
        Key.DirectionDown -> TvDir.Down
        else -> return@onPreviewKeyEvent if (e.type == KeyEventType.KeyDown) onOther(e.key) else false
    }
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    groups.move(dir, registry)
}

/** 成员把自己的焦点状态报给 [groups]（放在 `TvFocusable` / `TvButton` 的 modifier 里）。 */
fun Modifier.tvMember(groups: TvGroups, key: String): Modifier = onFocusChanged { groups.focusChanged(key, it.isFocused) }
