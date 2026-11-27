package com.gugaart.ui.tv

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gugaart.ui.Today
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.StateView
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.tv.detail.TvCollection
import com.gugaart.ui.tv.detail.TvMovie
import com.gugaart.ui.tv.detail.TvPersonScreen
import com.gugaart.ui.tv.detail.TvSeriesScreen
import com.gugugaga.app.ui.LocalGraph

/**
 * 列表/首页已拿到的 [Title] 轻量内存索引（按账号 + id 隔离，最多 128 条）：点进详情第一帧直接用它画头部、主按钮与背景，
 * 完整详情和剧集数据在后台静默补全，避免白屏等网返。
 */
internal object TvTitlePeekCache {
    private const val MAX = 128
    // 用插入序 LinkedHashMap 手动挪到队尾当 LRU（JVM 的 accessOrder 构造器在 Kotlin/Native 没有）。
    private val map = LinkedHashMap<String, Title>()

    fun putAll(account: String?, items: List<Title>) {
        if (account == null || items.isEmpty()) return
        for (t in items) {
            val k = "$account/${t.id}"
            map.remove(k)
            map[k] = t
            val sid = t.seriesId
            if (sid != null && sid != t.id && "$account/$sid" !in map) {
                map["$account/$sid"] = t.copy(id = sid, kind = Kind.Series, name = t.seriesName ?: t.name, episodeNo = null, seasonNo = null, seriesId = null)
            }
        }
        while (map.size > MAX) map.remove(map.keys.first())
    }

    fun get(account: String?, id: String): Title? {
        if (account == null) return null
        val k = "$account/$id"
        val v = map.remove(k) ?: return null
        map[k] = v
        return v
    }
}

/**
 * `Route.Item(id)`：剧集 / 电影 / 合集 / 人物。一屏一件事，上下键整屏翻（设计稿 `p=series|movie|coll|person`）。
 * 取数复用 `screens/detail/DetailData.kt`、`screens/sets/SetsData.kt`（和手机同一批加载函数、同一套兜底规则）；版式在 `tv/detail/`。
 * 方向键路径由每一屏自己的 `TvGroups` 描述（`tv/detail/TvNav.kt`），焦点登记 / 记忆 / 返回栈是上游的。
 * 单集（从首页「继续观看」的菜单进来）用电影那一版：播放 / 音轨 / 字幕 / 收藏 / 已看。
 */
@Composable
fun TvItemScreen(id: String, today: Today) {
    val g = LocalGraph.current
    val session by g.session.session.collectAsState()
    val account = session?.let { "${it.serverId}/${it.userId}" }
    val peek = remember(account, id) { TvTitlePeekCache.get(account, id) }
    val item = rememberLoad("tv-item", id) { c -> c.titleOf(c.getItem(id)) }
    val t = item.value ?: peek
    when {
        t == null && item.error != null -> TvPage("", "DETAIL", today) { ErrorView(item.error!!, item::retry) }
        t == null -> TvPage("", "DETAIL", today) { if (item.showSkeleton) Skeleton(Modifier.padding(top = 60.dp).width(620.dp).height(120.dp)) }
        t.kind == Kind.Series -> TvSeriesScreen(t.id, today, preview = t)
        t.kind == Kind.Person -> TvPersonScreen(t.id, today)
        t.kind == Kind.Movie || t.kind == Kind.Episode -> TvMovie(t, today)
        t.kind == Kind.BoxSet -> TvCollection(t, today)
        else -> TvPage(t.name, "DETAIL", today) { StateView("state-coming", "摆稿纸", "这一场还在筹备", "这一种条目敬请期待。") }
    }
}