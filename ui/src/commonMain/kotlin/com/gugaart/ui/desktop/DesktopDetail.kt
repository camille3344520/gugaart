package com.gugaart.ui.desktop

import androidx.compose.runtime.Composable
import com.gugaart.ui.Today
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.StateView
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.desktop.detail.DesktopMovie
import com.gugaart.ui.desktop.detail.DesktopPerson
import com.gugaart.ui.desktop.detail.DesktopSeries
import com.gugaart.ui.desktop.detail.DesktopSet

/**
 * `Route.Item(id)`：剧集 / 电影 / 单集（并入剧集详情，在目录上方展开）/ 合集 / 人物——整幅铺满（设计稿 `p=series|movie|coll|person`）。
 * 先取这个条目（和手机同一个入口：`getItem`），再按种类分。版式在 `desktop/detail/`；取数复用 `screens/detail/DetailData.kt`、`screens/sets/SetsData.kt`。
 */
@Composable
fun DesktopItemScreen(id: String, today: Today) {
    val item = rememberLoad("item", id) { c -> c.titleOf(c.getItem(id)) }
    val t = item.value
    when {
        t == null && item.error != null -> DesktopErrorPage(item.error!!, item::retry)
        t == null -> DesktopPage { if (item.showSkeleton) LoadingView() }
        t.kind == Kind.Series -> DesktopSeries(t.id, today)
        // 单集详情并入剧集页：打开它所属的剧，这一集在目录上方展开着；没有所属的剧（孤立的单集）按电影的版式排
        t.kind == Kind.Episode -> t.seriesId?.let { DesktopSeries(it, today, selected = t) } ?: DesktopMovie(t, today)
        t.kind == Kind.Movie -> DesktopMovie(t, today)
        t.kind == Kind.Person -> DesktopPerson(t.id, today)
        t.kind == Kind.BoxSet -> DesktopSet(t, today)
        else -> DesktopPage { StateView("state-coming", "摆稿纸", "这一场还在筹备", "这一种条目敬请期待。") }
    }
}
