package com.gugaart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.theme.Guga

/**
 * 状态视图的共同版式（docs/components.md §7）：她的一格（生图；还没生成的姿势是轮廓占位）+ 宋体标题 + 一句话 + 一个出路按钮。
 * 都排在页面流里自己的版面格里，不是浮层。她关掉时只剩文字和按钮。
 */
@Composable
fun StateView(
    tag: String,
    pose: String,
    title: String,
    line: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Guga.colors
    val type = Guga.type
    val speaks = LocalGugaPrefs.current.mascotSpeaks
    Column(modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin, vertical = 36.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        MascotPoseNamed(pose, 112.dp)
        BasicText(title, Modifier.padding(top = 20.dp), style = Guga.song(type.section, FontWeight.Black).copy(textAlign = TextAlign.Center))
        if (line != null && speaks) {
            BasicText(line, Modifier.padding(top = 10.dp), style = Guga.song(type.lead * 0.96f, color = colors.dim, lineHeight = type.leadLine * 0.9f).copy(textAlign = TextAlign.Center, fontStyle = FontStyle.Italic))
        }
        if (action != null && onAction != null) {
            PrimaryButton(action, onAction, Modifier.padding(top = 24.dp), icon = null)
        }
    }
}

/** 首次加载 / 片库加载：她摆稿纸 + 一句话。≥ 150 ms 才出现（由 `Load.showSkeleton` 管）。骨架由各页面自己排在它下面。 */
@Composable
fun LoadingView(modifier: Modifier = Modifier, seed: Int = 0) {
    StateView("state-loading", "摆稿纸", "正在排片", MascotLines.pick("wall.loading", hour = LocalHour.current, seed = seed), modifier)
}

/** 搜不到：她摊手 + 一句 +「换个词」。 */
@Composable
fun SearchNoneView(query: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    StateView("state-search-none", "摊手", "没有找到「$query」", MascotLines.pick("search.none", hour = LocalHour.current, seed = query.hashCode()), modifier, "换个词", onClear)
}

/** 搜索空状态：她举笔。 */
@Composable
fun SearchIdleView(modifier: Modifier = Modifier, seed: Int = 0) {
    StateView("state-search-idle", "举笔", "找哪一部", MascotLines.pick("search.idle", hour = LocalHour.current, seed = seed), modifier)
}

/** 筛不到：空状态里直接给「清掉 N 个筛选条件」。 */
@Composable
fun FilterNoneView(conditions: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    StateView("state-filter-none", "摊手", "筛空了", MascotLines.pick("wall.none", hour = LocalHour.current, seed = conditions), modifier, "清掉 $conditions 个筛选条件", onClear)
}

/** 媒体库是空的 / 这台服务器上没有库：整页说明，不是空白墙。 */
@Composable
fun EmptyLibraryView(modifier: Modifier = Modifier) {
    StateView("state-empty-library", "捧白纸", "这里还没有内容", "片架上是空的。往服务器里放几部片子，再回来。", modifier)
}

/** 整页错误：断网（拔掉的电源插头）/ 服务器没应答（摊手）/ 登录失效。原文不上版面。 */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val offline = message.contains("网络")
    // 登录失效 = 读者证过期：她捧白纸（G10）
    val expired = message.contains("登录失效")
    StateView(
        "state-error",
        if (offline) "拔掉的电源插头" else if (expired) "捧白纸" else "摊手",
        message,
        if (offline) MascotLines.pick("offline", hour = LocalHour.current, seed = 0) else "稍等一下再试；一直不行就换一条线路。",
        modifier,
        "再试一次",
        onRetry,
    )
}

/**
 * 首页顶上的一条「断线」（设计稿 `home-offline`）：已有内容保留，只在报头上方多一条——朱红「断线」签 + 一句话 + 「重试」。
 * 排在页面流里（把报头往下推），不盖内容。
 */
@Composable
fun OfflineBar(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin).padding(bottom = 10.dp).testTag("offline-bar"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("断线", Modifier.background(colors.seal).padding(horizontal = 6.dp, vertical = 2.dp), style = Guga.song(Guga.type.kicker, FontWeight.Black, colors.ink, letterSpacing = 0.2.em), maxLines = 1)
        BasicText("连不上服务器，先放上次缓存的片单", Modifier.weight(1f), style = Guga.song(Guga.type.caption, color = colors.dim), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (onRetry != null) TextLink("重试", onRetry)
    }
}

/** 首页末尾：她打盹，不是无限加载。 */
@Composable
fun EndOfPage(modifier: Modifier = Modifier, seed: Int = 0) {
    val colors = Guga.colors
    Column(modifier.fillMaxWidth().padding(top = 28.dp).testTag("end-of-page"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(40.dp).height(1.dp).background(colors.line))
        MascotPoseNamed("打盹", 96.dp, Modifier.padding(top = 22.dp))
        if (LocalGugaPrefs.current.mascotSpeaks) {
            MascotLines.pick("home.end", LineFacts(), LocalHour.current, seed)?.let {
                BasicText(it, Modifier.padding(top = 10.dp), style = Guga.song(Guga.type.caption, color = colors.dim).copy(fontStyle = FontStyle.Italic))
            }
        }
    }
}
