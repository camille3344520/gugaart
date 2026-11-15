package com.gugaart.ui.tv

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gugaart.ui.Today
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.StateView
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.tv.detail.TvCollection
import com.gugaart.ui.tv.detail.TvMovie
import com.gugaart.ui.tv.detail.TvPersonScreen
import com.gugaart.ui.tv.detail.TvSeriesScreen

/**
 * `Route.Item(id)`：剧集 / 电影 / 合集 / 人物。一屏一件事，上下键整屏翻（设计稿 `p=series|movie|coll|person`）。
 * 取数复用 `screens/detail/DetailData.kt`、`screens/sets/SetsData.kt`（和手机同一批加载函数、同一套兜底规则）；版式在 `tv/detail/`。
 * 方向键路径由每一屏自己的 `TvGroups` 描述（`tv/detail/TvNav.kt`），焦点登记 / 记忆 / 返回栈是上游的。
 * 单集（从首页「继续观看」的菜单进来）用电影那一版：播放 / 音轨 / 字幕 / 收藏 / 已看。
 */
@Composable
fun TvItemScreen(id: String, today: Today) {
    val item = rememberLoad("tv-item", id) { c -> c.titleOf(c.getItem(id)) }
    val t = item.value
    when {
        t == null && item.error != null -> TvPage("", "DETAIL", today) { ErrorView(item.error!!, item::retry) }
        t == null -> TvPage("", "DETAIL", today) { if (item.showSkeleton) Skeleton(Modifier.padding(top = 60.dp).width(620.dp).height(120.dp)) }
        t.kind == Kind.Series -> TvSeriesScreen(t.id, today)
        t.kind == Kind.Person -> TvPersonScreen(t.id, today)
        t.kind == Kind.Movie || t.kind == Kind.Episode -> TvMovie(t, today)
        t.kind == Kind.BoxSet -> TvCollection(t, today)
        else -> TvPage(t.name, "DETAIL", today) { StateView("state-coming", "摆稿纸", "这一场还在筹备", "这一种条目敬请期待。") }
    }
}
